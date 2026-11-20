package com.delivery.dispatch.grpc;

import com.delivery.dispatch.dto.LocationPointRequest;
import com.delivery.tracking.grpcclient.TrackingGrpcException;
import io.grpc.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveLocationBidiStreamingIntegrationTest {

    static final int MAX_ATTEMPTS = 4;
    static final int RESTART_TESTS = Integer.MAX_VALUE - 10;

    static final FakeTrackingServer server = new FakeTrackingServer();

    static {
        try {
            server.start();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-bidi-grpc-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // H2 без Flyway: міграції написані для Postgres і перевіряються в DispatchFlowIntegrationTest
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
        registry.add("outbox.relay.enabled", () -> "false");
        registry.add("dispatch.scheduling.enabled", () -> "false");
        registry.add("TRACKING_GRPC_TARGET", () -> "dns:///127.0.0.1:" + server.port());
        registry.add("tracking.grpc.retry.max-attempts", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("tracking.grpc.retry.initial-backoff", () -> "50ms");
        registry.add("tracking.grpc.retry.max-backoff", () -> "200ms");
    }

    @Autowired
    private TrackingGrpcClient client;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        server.start();
        server.reset();
        awaitSuccess(() -> client.streamLiveLocations(1, List.of(
                new LocationPointRequest(50.45, 30.52, Instant.parse("2026-11-15T10:00:00Z"))
        )));
        server.reset();
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        server.stop();
    }

    @Test
    void bidirectionalStreamingProcessesAcceptedRejectedAndSuspiciousPoints() throws Exception {
        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"points":[
                                  {"latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                                  {"latitude":95.0,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                                  {"latitude":48.0000,"longitude":24.0000,"recordedAt":"2026-11-15T10:00:20Z"},
                                  {"latitude":50.4520,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:30Z"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.sentCount").value(4))
                .andExpect(jsonPath("$.acceptedCount").value(2))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.suspiciousJumpCount").value(1))
                .andExpect(jsonPath("$.decisions[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.decisions[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.decisions[2].status").value("SUSPICIOUS_JUMP"))
                .andExpect(jsonPath("$.decisions[3].status").value("ACCEPTED"));

        assertThat(server.liveStreamCalls).hasValue(1);
    }

    @Test
    void bidirectionalStreamingRetriesOnUnavailable(CapturedOutput output) throws Exception {
        server.failNextCalls(2, Status.UNAVAILABLE.withDescription("simulated outage"));

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(3));

        assertThat(server.liveStreamCalls).hasValue(3);
        assertThat(output).contains("gRPC retry attempt: method=StreamCourierLocations, attempt=3");
    }

    @Test
    void bidirectionalStreamingReturns503WhenAllRetriesExhausted() throws Exception {
        server.failNextCalls(100, Status.UNAVAILABLE.withDescription("prolonged outage"));

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));

        assertThat(server.liveStreamCalls).hasValue(MAX_ATTEMPTS);
    }

    @Test
    @Order(RESTART_TESTS)
    void bidirectionalStreamingRecoversAfterServerStopAndRestart() throws Exception {
        server.stop();

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isServiceUnavailable());

        server.start();

        awaitSuccess(() -> client.streamLiveLocations(15, List.of(
                new LocationPointRequest(50.4501, 30.5234, Instant.parse("2026-11-15T10:00:00Z"))
        )));
        assertThat(server.receivedLiveStreams).isNotEmpty();
    }

    private static String threeValidPointsJson() {
        return """
                {"points":[
                  {"latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                  {"latitude":50.4510,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                  {"latitude":50.4520,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:20Z"}
                ]}
                """;
    }

    private static void awaitSuccess(Runnable call) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptionsMatching(ex -> ex instanceof TrackingGrpcException grpc
                        && grpc.getCode() == Status.Code.UNAVAILABLE)
                .untilAsserted(call::run);
    }
}
