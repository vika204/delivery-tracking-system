package com.delivery.notification.grpc;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeliveryInfoUnaryIntegrationTest {

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

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
        registry.add("logging.level.org.apache.kafka", () -> "ERROR");
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
        awaitSuccess(() -> client.getDeliveryInfo(1));
        server.reset();
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        server.stop();
    }

    @Test
    void returnsStatusAndLatestLocation() throws Exception {
        mockMvc.perform(get("/notifications/tracking/shipments/15/delivery-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.latitude").value(50.4501))
                .andExpect(jsonPath("$.longitude").value(30.5234))
                .andExpect(jsonPath("$.recordedAt").value("2026-11-06T21:20:00Z"));

        assertThat(server.unaryCalls).hasValue(1);
    }

    @Test
    void returnsStatusWithoutLocationWhenNoCoordinatesYet() throws Exception {
        server.hasLocation = false;

        mockMvc.perform(get("/notifications/tracking/shipments/15/delivery-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_COURIER"))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.recordedAt").doesNotExist());
    }

    @Test
    void retriesUnavailableAndSucceeds(CapturedOutput output) {
        server.failNextCalls(2, Status.UNAVAILABLE.withDescription("simulated outage"));

        var info = client.getDeliveryInfo(15);

        assertThat(info.status()).isEqualTo("IN_TRANSIT");
        assertThat(server.unaryCalls).hasValue(3);
        assertThat(output)
                .contains("gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=3")
                .contains("gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=1, status=UNAVAILABLE");
    }

    @Test
    void returns503WhenRetriesExhausted() throws Exception {
        server.failNextCalls(100, Status.UNAVAILABLE.withDescription("simulated outage"));

        mockMvc.perform(get("/notifications/tracking/shipments/15/delivery-info"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.grpcStatus").value("UNAVAILABLE"));

        assertThat(server.unaryCalls).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void doesNotRetryNotFound() throws Exception {
        server.failNextCalls(1, Status.NOT_FOUND.withDescription("shipment not found"));

        mockMvc.perform(get("/notifications/tracking/shipments/15/delivery-info"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.grpcStatus").value("NOT_FOUND"));

        assertThat(server.unaryCalls).hasValue(1);
    }

    @Test
    void rejectsInvalidShipmentIdBeforeCallingGrpc() throws Exception {
        mockMvc.perform(get("/notifications/tracking/shipments/0/delivery-info"))
                .andExpect(status().isBadRequest());

        assertThat(server.unaryCalls).hasValue(0);
    }

    @Test
    @Order(RESTART_TESTS)
    void recoversAfterServerRestart(CapturedOutput output) throws Exception {
        server.stop();

        mockMvc.perform(get("/notifications/tracking/shipments/15/delivery-info"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.grpcStatus").value("UNAVAILABLE"));

        assertThat(output).contains("gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=" + MAX_ATTEMPTS);
        assertThat(server.unaryCalls).hasValue(0);

        server.start();

        awaitSuccess(() -> client.getDeliveryInfo(15));
        assertThat(server.unaryCalls.get()).isPositive();
    }

        private static void awaitSuccess(Runnable call) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptionsMatching(ex -> ex instanceof TrackingGrpcException grpc
                        && grpc.getCode() == Status.Code.UNAVAILABLE)
                .untilAsserted(call::run);
    }
}
