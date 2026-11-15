package com.delivery.dispatch.tracking;

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

import com.delivery.dispatch.tracking.dto.RoutePointRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Інтеграційні тести Client Streaming-клієнта dispatch-service:
 * реальний Spring-контекст, канал із service config із application.properties
 * (перевизначено лише затримки) і реальний gRPC-сервер на TCP-порту.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
// Тести з повною зупинкою сервера виконуються останніми, щоб стан каналу після
// рестарту (reconnect backoff) не впливав на інші тести.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentRouteGrpcClientRetryIntegrationTest {

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-grpc-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("TRACKING_GRPC_TARGET", () -> "dns:///127.0.0.1:" + server.port());
        registry.add("TRACKING_GRPC_RETRY_MAX_ATTEMPTS", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("TRACKING_GRPC_RETRY_INITIAL_BACKOFF", () -> "50ms");
        registry.add("TRACKING_GRPC_RETRY_MAX_BACKOFF", () -> "200ms");
    }

    @Autowired
    private ShipmentRouteGrpcClient client;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        ensureServerRunning();
        // Після тестів із рестартом сервера канал може ще бути в TRANSIENT_FAILURE —
        // кожен тест починаємо з робочого з'єднання й нульових лічильників.
        server.reset();
        awaitSuccess(() -> client.uploadRoute(1, "warm-up-" + System.nanoTime(), List.of(
                new RoutePointRequest(50.45, 30.52, Instant.parse("2026-11-15T10:00:00Z")))));
        server.reset();
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        server.stop();
    }

    // ---------- Client streaming ----------

    @Test
    void clientStreamingUploadsAllPoints() throws Exception {
        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .header("Idempotency-Key", "upload-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("upload-1"))
                .andExpect(jsonPath("$.receivedCount").value(3))
                .andExpect(jsonPath("$.savedCount").value(3))
                .andExpect(jsonPath("$.rejectedCount").value(0));

        assertThat(server.routeCalls).hasValue(1);
        assertThat(server.receivedRoutes).hasSize(1);
        assertThat(server.receivedRoutes.getFirst())
                .allSatisfy(point -> {
                    assertThat(point.getUploadId()).isEqualTo("upload-1");
                    assertThat(point.getShipmentId()).isEqualTo(15);
                });
    }

    @Test
    void clientStreamingRetryReplaysWholeStreamWithSameUploadId(CapturedOutput output) throws Exception {
        server.failNextCalls(2, Status.UNAVAILABLE.withDescription("simulated outage"));

        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .header("Idempotency-Key", "upload-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(3));

        assertThat(server.routeCalls).hasValue(3);
        // Кожна спроба отримала ПОВНИЙ потік із тим самим upload_id — gRPC відтворив буфер повідомлень.
        assertThat(server.receivedRoutes).hasSize(3)
                .allSatisfy(route -> {
                    assertThat(route).hasSize(3);
                    assertThat(route).allSatisfy(point -> assertThat(point.getUploadId()).isEqualTo("upload-retry"));
                });
        assertThat(output).contains("gRPC retry attempt: method=RecordShipmentRoute, attempt=3");
    }

    @Test
    void clientStreamingReturns503WhenRetriesExhausted() throws Exception {
        server.failNextCalls(100, Status.UNAVAILABLE.withDescription("simulated outage"));

        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", TrackingGrpcExceptionHandler.RETRY_AFTER_SECONDS));

        assertThat(server.routeCalls).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void clientStreamingDoesNotRetryInvalidArgument() throws Exception {
        server.failNextCalls(1, Status.INVALID_ARGUMENT.withDescription("bad point"));

        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.grpcStatus").value("INVALID_ARGUMENT"));

        assertThat(server.routeCalls).hasValue(1);
    }

    @Test
    @Order(RESTART_TESTS)
    void clientStreamingRecoversAfterServerRestart() throws Exception {
        server.stop();

        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson()))
                .andExpect(status().isServiceUnavailable());

        server.start();

        awaitSuccess(() -> client.uploadRoute(15, "after-restart", List.of(
                new RoutePointRequest(50.45, 30.52, Instant.parse("2026-11-15T10:00:00Z"))
        )));
        assertThat(server.receivedRoutes).isNotEmpty();
    }

    @Test
    void invalidCoordinatesAreSentToServerAndCountedAsRejected() throws Exception {
        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"points":[
                                  {"latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                                  {"latitude":95,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                                  {"latitude":50.4520,"longitude":30.5250}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedCount").value(3))
                .andExpect(jsonPath("$.savedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(2));

        // Перевірку координат виконує сервер: усі три точки дійшли до нього.
        assertThat(server.receivedRoutes.getFirst()).hasSize(3);
        assertThat(server.receivedRoutes.getFirst().get(2).hasRecordedAt()).isFalse();
    }

    @Test
    void rejectsEmptyRouteBeforeCallingGrpc() throws Exception {
        mockMvc.perform(post("/dispatch/shipments/15/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":[]}"))
                .andExpect(status().isBadRequest());

        assertThat(server.routeCalls).hasValue(0);
    }

    // ---------- helpers ----------

    private static String routeJson() {
        return """
                {"points":[
                  {"latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
                  {"latitude":50.4510,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
                  {"latitude":50.4520,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:20Z"}
                ]}
                """;
    }

    private static void ensureServerRunning() throws IOException {
        server.start();
    }

    /**
     * Після рестарту канал може ще перебувати в TRANSIENT_FAILURE і чекати свого connect-backoff,
     * тому перевіряємо, що виклики відновлюються протягом розумного часу, а не миттєво.
     */
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
