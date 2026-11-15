package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.repository.LiveLocationReceiptRepository;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.TrackingService;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Інтеграційні тести серверної частини Bidirectional Streaming (StreamCourierLocations)
 * та серверного логування gRPC-викликів (GrpcServerLoggingInterceptor).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class)
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers
class ShipmentTrackingBidirectionalStreamingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_bidi_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("tracking.recorded-at-zone", () -> "UTC");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub;

    @Autowired
    private TrackingRepository trackingRepository;

    @Autowired
    private LiveLocationReceiptRepository liveLocationReceiptRepository;

    @MockitoBean
    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        liveLocationReceiptRepository.deleteAll();
        trackingRepository.deleteAll();
        when(trackingService.getShipmentById(anyLong()))
                .thenAnswer(invocation -> new ShipmentDto(invocation.getArgument(0), "Recipient", "IN_TRANSIT"));
    }

    @Test
    void bidirectionalStreamingAcceptsValidPointsAndCalculatesDistanceAndSpeed(CapturedOutput output) throws Exception {
        List<CourierLocationDecision> decisions = streamLive(List.of(
                report("pt-1", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                report("pt-2", 15L, 50.4510, 30.5240, "2026-11-15T10:00:15Z"),
                report("pt-3", 15L, 50.4520, 30.5250, "2026-11-15T10:00:30Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(decisions).hasSize(3);
        assertThat(decisions).allSatisfy(d -> {
            assertThat(d.getShipmentId()).isEqualTo(15L);
            assertThat(d.getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED);
            assertThat(d.getSaved()).isTrue();
            assertThat(d.getLocationId()).isPositive();
            assertThat(d.getDuplicate()).isFalse();
        });

        assertThat(decisions.get(0).getDistanceMeters()).isZero();
        assertThat(decisions.get(1).getDistanceMeters()).isGreaterThan(50.0);
        assertThat(decisions.get(1).getSpeedKmh()).isGreaterThan(10.0);
        assertThat(trackingRepository.count()).isEqualTo(3);

        // Перевірка серверного логування gRPC-виклику.
        assertThat(output).contains("gRPC server call started: method=StreamCourierLocations");
        assertThat(output).contains("gRPC server call completed: method=StreamCourierLocations, status=OK");
    }

    @Test
    void bidirectionalStreamingHandlesAcceptedRejectedAndSuspiciousJumpWithoutClosingStream() throws Exception {
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        List<CourierLocationDecision> decisions = streamLive(List.of(
                // 1. Перша коректна точка у Києві -> ACCEPTED
                report("pt-mix-1", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                // 2. Некоректна широта -> REJECTED
                report("pt-mix-2", 15L, 95.0, 30.5240, "2026-11-15T10:00:10Z"),
                // 3. Відсутній recordedAt -> REJECTED
                reportWithoutTime("pt-mix-3", 15L, 50.4510, 30.5240),
                // 4. Час у майбутньому -> REJECTED
                report("pt-mix-4", 15L, 50.4510, 30.5240, future),
                // 5. Підозрілий стрибок на ~470 км (до Львова за 10 секунд) -> SUSPICIOUS_JUMP
                report("pt-mix-5", 15L, 49.8397, 24.0297, "2026-11-15T10:00:20Z"),
                // 6. Наступна нормальна точка біля першої у Києві -> ACCEPTED (потік не перервався!)
                report("pt-mix-6", 15L, 50.4515, 30.5250, "2026-11-15T10:00:30Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(decisions).hasSize(6);

        assertThat(decisions.get(0).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED);
        assertThat(decisions.get(0).getSaved()).isTrue();

        assertThat(decisions.get(1).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED);
        assertThat(decisions.get(1).getSaved()).isFalse();
        assertThat(decisions.get(1).getReason()).contains("latitude");

        assertThat(decisions.get(2).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED);
        assertThat(decisions.get(2).getSaved()).isFalse();
        assertThat(decisions.get(2).getReason()).contains("recordedAt is required");

        assertThat(decisions.get(3).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED);
        assertThat(decisions.get(3).getSaved()).isFalse();
        assertThat(decisions.get(3).getReason()).contains("future");

        assertThat(decisions.get(4).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP);
        assertThat(decisions.get(4).getSaved()).isFalse();
        assertThat(decisions.get(4).getDistanceMeters()).isGreaterThan(100_000.0);

        assertThat(decisions.get(5).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED);
        assertThat(decisions.get(5).getSaved()).isTrue();

        // У БД збережено рівно 2 коректні точки (1-шу і 6-ту).
        assertThat(trackingRepository.count()).isEqualTo(2);
    }

    @Test
    void bidirectionalStreamingRetryWithSamePointIdIsIdempotent() throws Exception {
        CourierLocationReport point = report("pt-idem-1", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z");

        List<CourierLocationDecision> firstRun = streamLive(List.of(point)).get(10, TimeUnit.SECONDS);
        List<CourierLocationDecision> secondRun = streamLive(List.of(point)).get(10, TimeUnit.SECONDS);

        assertThat(firstRun).hasSize(1);
        assertThat(secondRun).hasSize(1);

        assertThat(firstRun.getFirst().getDuplicate()).isFalse();
        assertThat(secondRun.getFirst().getDuplicate()).isTrue();
        assertThat(secondRun.getFirst().getLocationId()).isEqualTo(firstRun.getFirst().getLocationId());
        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    @Test
    void bidirectionalStreamingRejectsPointIdReusedForAnotherShipmentWithoutClosingStream() throws Exception {
        streamLive(List.of(report("pt-shared", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z")))
                .get(10, TimeUnit.SECONDS);

        List<CourierLocationDecision> decisions = streamLive(List.of(
                report("pt-shared", 16L, 50.4501, 30.5234, "2026-11-15T10:00:05Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(decisions).hasSize(1);
        assertThat(decisions.getFirst().getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED);
        assertThat(decisions.getFirst().getSaved()).isFalse();
        assertThat(decisions.getFirst().getReason()).contains("already used for another shipment");
        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    private static CourierLocationReport report(String pointId, long shipmentId, double lat, double lon, String recordedAt) {
        Instant instant = Instant.parse(recordedAt);
        return reportWithoutTime(pointId, shipmentId, lat, lon).toBuilder()
                .setRecordedAt(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
                .build();
    }

    private static CourierLocationReport reportWithoutTime(String pointId, long shipmentId, double lat, double lon) {
        return CourierLocationReport.newBuilder()
                .setPointId(pointId)
                .setShipmentId(shipmentId)
                .setLatitude(lat)
                .setLongitude(lon)
                .build();
    }

    private CompletableFuture<List<CourierLocationDecision>> streamLive(List<CourierLocationReport> reports) {
        CompletableFuture<List<CourierLocationDecision>> result = new CompletableFuture<>();
        List<CourierLocationDecision> decisions = new CopyOnWriteArrayList<>();

        StreamObserver<CourierLocationReport> requestObserver = asyncStub.streamCourierLocations(new StreamObserver<>() {
            @Override
            public void onNext(CourierLocationDecision decision) {
                decisions.add(decision);
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onCompleted() {
                result.complete(decisions);
            }
        });

        reports.forEach(requestObserver::onNext);
        requestObserver.onCompleted();
        return result;
    }
}
