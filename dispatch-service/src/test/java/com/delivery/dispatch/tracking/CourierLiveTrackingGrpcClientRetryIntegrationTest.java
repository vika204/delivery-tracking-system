package com.delivery.dispatch.tracking;

import com.delivery.dispatch.tracking.dto.LiveLocationPointRequest;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Інтеграційні тести Bidirectional Streaming-клієнта dispatch-service (StreamCourierLocations):
 * перевіряє обробку ACCEPTED / REJECTED / SUSPICIOUS_JUMP у реальному часі,
 * автоматичні retries на рівні gRPC service-config, перепідключення при обриві посеред потоку,
 * повернення HTTP 503 після вичерпання спроб і відновлення після рестарту сервера.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourierLiveTrackingGrpcClientRetryIntegrationTest {

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
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
        registry.add("outbox.relay.enabled", () -> "false");
        registry.add("dispatch.scheduling.enabled", () -> "false");
        registry.add("TRACKING_GRPC_TARGET", () -> "dns:///127.0.0.1:" + server.port());
        registry.add("TRACKING_GRPC_RETRY_MAX_ATTEMPTS", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("TRACKING_GRPC_RETRY_INITIAL_BACKOFF", () -> "50ms");
        registry.add("TRACKING_GRPC_RETRY_MAX_BACKOFF", () -> "200ms");
    }

    @Autowired
    private CourierLiveTrackingGrpcClient client;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        server.start();
        server.reset();
        awaitSuccess(() -> client.streamLocations(1, "warm-up-" + System.nanoTime(), List.of(
                new LiveLocationPointRequest("warmup-pt", 50.45, 30.52, Instant.parse("2026-11-15T10:00:00Z"))
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
                        .header("Idempotency-Key", "session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"points":[
                                  {"pointId":"pt-1","latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                                  {"pointId":"pt-2","latitude":95.0,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                                  {"pointId":"pt-3","latitude":48.0000,"longitude":24.0000,"recordedAt":"2026-11-15T10:00:20Z"},
                                  {"pointId":"pt-4","latitude":50.4520,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:30Z"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
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
    void bidirectionalStreamingRetriesOnInitialUnavailableViaServiceConfig(CapturedOutput output) throws Exception {
        server.failNextCalls(2, Status.UNAVAILABLE.withDescription("simulated initial outage"));

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .header("Idempotency-Key", "session-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(3));

        assertThat(server.liveStreamCalls).hasValue(3);
        assertThat(output).contains("gRPC retry attempt: method=StreamCourierLocations, attempt=3");
    }

    @Test
    void bidirectionalStreamingReconnectsAndSendsOnlyRemainingPointsOnMidStreamUnavailable(CapturedOutput output) throws Exception {
        // Перший потік підтверджує 1 точку і обривається з UNAVAILABLE; клієнт перепідключається й досилає решту 2 точки.
        server.failMidStreamNextCalls(1, 1, Status.UNAVAILABLE.withDescription("connection reset mid-stream"));

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .header("Idempotency-Key", "session-midstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(3))
                .andExpect(jsonPath("$.acceptedCount").value(3));

        assertThat(server.liveStreamCalls).hasValue(2);
        assertThat(server.receivedLiveStreams).hasSize(2);
        assertThat(server.receivedLiveStreams.get(0)).hasSize(3);
        // У другому підключенні клієнт надіслав лише 2 точки, які не отримали підтвердження в першому потоці!
        assertThat(server.receivedLiveStreams.get(1)).hasSize(2);
        assertThat(output).contains("gRPC StreamCourierLocations interrupted mid-stream with UNAVAILABLE; reconnecting");
    }

    @Test
    void bidirectionalStreamingReturns503WhenAllRetriesExhausted() throws Exception {
        server.failNextCalls(100, Status.UNAVAILABLE.withDescription("prolonged outage"));

        mockMvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeValidPointsJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", TrackingGrpcExceptionHandler.RETRY_AFTER_SECONDS));

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

        awaitSuccess(() -> client.streamLocations(15, "after-restart", List.of(
                new LiveLocationPointRequest("pt-r1", 50.4501, 30.5234, Instant.parse("2026-11-15T10:00:00Z"))
        )));
        assertThat(server.receivedLiveStreams).isNotEmpty();
    }

    private static String threeValidPointsJson() {
        return """
                {"points":[
                  {"pointId":"pt-1","latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                  {"pointId":"pt-2","latitude":50.4510,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                  {"pointId":"pt-3","latitude":50.4520,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:20Z"}
                ]}
                """;
    }

    private static void awaitSuccess(Runnable call) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        TrackingGrpcException last = null;

        while (System.nanoTime() < deadline) {
            try {
                call.run();
                return;
            } catch (TrackingGrpcException ex) {
                assertThat(ex.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                last = ex;
                Thread.sleep(200);
            }
        }
        throw new AssertionError("call did not recover after server restart", last);
    }
}
