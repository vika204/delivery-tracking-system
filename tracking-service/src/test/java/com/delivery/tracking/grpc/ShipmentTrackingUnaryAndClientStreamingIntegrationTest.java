package com.delivery.tracking.grpc;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.RouteUploadRepository;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.LocationService;
import com.google.protobuf.Timestamp;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Серверна частина Unary та Client Streaming: реальний gRPC-виклик і PostgreSQL (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = {
        ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class,
        ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub.class
})
@Testcontainers
class ShipmentTrackingUnaryAndClientStreamingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_test_db")
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
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub;

    @Autowired
    private LocationService locationService;

    @Autowired
    private TrackingRepository trackingRepository;

    @Autowired
    private RouteUploadRepository routeUploadRepository;

    @BeforeEach
    void setUp() {
        routeUploadRepository.deleteAll();
        trackingRepository.deleteAll();
    }

    // ---------- Unary ----------

    @Test
    void unaryReturnsLatestLocationByRecordedAt() {
        save(15L, "50.100000", "30.100000", LocalDateTime.of(2026, 11, 15, 10, 0));
        Location latest = save(15L, "50.300000", "30.300000", LocalDateTime.of(2026, 11, 15, 10, 30));
        // Вставлена пізніше, але записана раніше (наприклад, через вивантаження маршруту) — не остання.
        save(15L, "50.200000", "30.200000", LocalDateTime.of(2026, 11, 15, 10, 15));
        save(20L, "49.000000", "28.000000", LocalDateTime.of(2026, 11, 15, 11, 0));

        ShipmentLocationUpdate update = blockingStub.getLatestShipmentLocation(request(15L));

        assertThat(update.getLocationId()).isEqualTo(latest.getLocationId());
        assertThat(update.getShipmentId()).isEqualTo(15L);
        assertThat(update.getLatitude()).isCloseTo(50.3, within(0.000001));
        assertThat(update.getRecordedAt().getSeconds()).isEqualTo(Instant.parse("2026-11-15T10:30:00Z").getEpochSecond());
    }

    @Test
    void unaryReturnsNotFoundWhenNoLocations() {
        assertThatThrownBy(() -> blockingStub.getLatestShipmentLocation(request(99L)))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void unaryReturnsInvalidArgumentForInvalidShipmentId() {
        assertThatThrownBy(() -> blockingStub.getLatestShipmentLocation(request(0L)))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ---------- Client streaming ----------

    @Test
    void clientStreamingSavesWholeRouteAndReturnsSummary() throws Exception {
        ShipmentRouteSummary summary = upload(List.of(
                point("upload-1", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-1", 15L, 50.4510, 30.5240, "2026-11-15T10:00:10Z"),
                point("upload-1", 15L, 50.4520, 30.5250, "2026-11-15T10:00:20Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(summary.getUploadId()).isEqualTo("upload-1");
        assertThat(summary.getShipmentId()).isEqualTo(15L);
        assertThat(summary.getAcceptedCount()).isEqualTo(3);
        assertThat(summary.getDuplicate()).isFalse();
        assertThat(summary.getLastLocationId()).isGreaterThan(summary.getFirstLocationId());

        assertThat(trackingRepository.count()).isEqualTo(3);
        assertThat(routeUploadRepository.findById("upload-1")).isPresent();

        // Unary бачить останню точку щойно вивантаженого маршруту.
        ShipmentLocationUpdate latest = blockingStub.getLatestShipmentLocation(request(15L));
        assertThat(latest.getLocationId()).isEqualTo(summary.getLastLocationId());
        assertThat(latest.getLatitude()).isCloseTo(50.4520, within(0.000001));
    }

    @Test
    void clientStreamingRetryWithSameUploadIdDoesNotDuplicatePoints() throws Exception {
        List<ShipmentRoutePoint> route = List.of(
                point("upload-retry", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-retry", 15L, 50.4510, 30.5240, "2026-11-15T10:00:10Z")
        );

        ShipmentRouteSummary first = upload(route).get(10, TimeUnit.SECONDS);
        // Імітація retry: сервер зберіг дані, але клієнт не отримав відповідь і повторив потік.
        ShipmentRouteSummary second = upload(route).get(10, TimeUnit.SECONDS);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(second.getDuplicate()).isTrue();
        assertThat(second.getAcceptedCount()).isEqualTo(2);
        assertThat(second.getFirstLocationId()).isEqualTo(first.getFirstLocationId());
        assertThat(second.getLastLocationId()).isEqualTo(first.getLastLocationId());
        assertThat(trackingRepository.count()).isEqualTo(2);
    }

    @Test
    void clientStreamingRejectsUploadIdReusedForAnotherShipment() throws Exception {
        upload(List.of(point("upload-x", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"))).get(10, TimeUnit.SECONDS);

        assertThat(statusOf(upload(List.of(point("upload-x", 16L, 50.45, 30.52, "2026-11-15T10:00:00Z")))))
                .isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    @Test
    void clientStreamingRejectsInvalidPointAndSavesNothing() throws Exception {
        CompletableFuture<ShipmentRouteSummary> result = upload(List.of(
                point("upload-bad", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-bad", 15L, 95.0, 30.5240, "2026-11-15T10:00:10Z")
        ));

        assertThat(statusOf(result)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(trackingRepository.count()).isZero();
        assertThat(routeUploadRepository.count()).isZero();
    }

    @Test
    void clientStreamingRejectsMixedShipmentIds() throws Exception {
        CompletableFuture<ShipmentRouteSummary> result = upload(List.of(
                point("upload-mixed", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"),
                point("upload-mixed", 16L, 50.46, 30.53, "2026-11-15T10:00:10Z")
        ));

        assertThat(statusOf(result)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(trackingRepository.count()).isZero();
    }

    @Test
    void clientStreamingRejectsEmptyRoute() throws Exception {
        assertThat(statusOf(upload(List.of()))).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void clientStreamingAbortedByClientSavesNothing() throws Exception {
        CompletableFuture<ShipmentRouteSummary> result = new CompletableFuture<>();
        Context.CancellableContext context = Context.current().withCancellation();

        StreamObserver<ShipmentRoutePoint> requestObserver = context.call(() -> asyncStub.recordShipmentRoute(collector(result)));
        requestObserver.onNext(point("upload-abort", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"));
        requestObserver.onNext(point("upload-abort", 15L, 50.46, 30.53, "2026-11-15T10:00:10Z"));
        // Обрив з'єднання посеред потоку: onCompleted так і не надходить.
        context.cancel(null);

        assertThat(statusOf(result)).isEqualTo(Status.Code.CANCELLED);
        Thread.sleep(200);
        assertThat(trackingRepository.count()).isZero();
        assertThat(routeUploadRepository.count()).isZero();
    }

    @Test
    void clientStreamingPointsArePublishedToServerStreamingSubscribers() throws Exception {
        BlockingQueue<ShipmentLocationUpdate> received = new LinkedBlockingQueue<>();
        Context.CancellableContext context = Context.current().withCancellation();

        try {
            context.run(() -> asyncStub.subscribeToShipmentLocations(
                    SubscribeToShipmentLocationsRequest.newBuilder().setShipmentId(15L).build(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(ShipmentLocationUpdate update) {
                            received.add(update);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }

                        @Override
                        public void onCompleted() {
                        }
                    }));

            // Чекаємо реєстрації підписки на сервері, повторюючи вивантаження з новим upload_id.
            ShipmentLocationUpdate update = null;
            for (int i = 0; i < 25 && update == null; i++) {
                upload(List.of(point("upload-pub-" + i, 15L, 50.45, 30.52, "2026-11-15T10:00:00Z")))
                        .get(10, TimeUnit.SECONDS);
                update = received.poll(200, TimeUnit.MILLISECONDS);
            }

            assertThat(update).isNotNull();
            assertThat(update.getShipmentId()).isEqualTo(15L);
        } finally {
            context.cancel(null);
        }
    }

    // ---------- helpers ----------

    private Location save(long shipmentId, String latitude, String longitude, LocalDateTime recordedAt) {
        return locationService.createLocation(new Location(
                null, shipmentId, new BigDecimal(latitude), new BigDecimal(longitude), recordedAt));
    }

    private static GetLatestShipmentLocationRequest request(long shipmentId) {
        return GetLatestShipmentLocationRequest.newBuilder().setShipmentId(shipmentId).build();
    }

    private static ShipmentRoutePoint point(String uploadId, long shipmentId, double lat, double lon, String recordedAt) {
        Instant instant = Instant.parse(recordedAt);
        return ShipmentRoutePoint.newBuilder()
                .setUploadId(uploadId)
                .setShipmentId(shipmentId)
                .setLatitude(lat)
                .setLongitude(lon)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
                .build();
    }

    private CompletableFuture<ShipmentRouteSummary> upload(List<ShipmentRoutePoint> points) {
        CompletableFuture<ShipmentRouteSummary> result = new CompletableFuture<>();
        StreamObserver<ShipmentRoutePoint> requestObserver = asyncStub.recordShipmentRoute(collector(result));
        points.forEach(requestObserver::onNext);
        requestObserver.onCompleted();
        return result;
    }

    private static StreamObserver<ShipmentRouteSummary> collector(CompletableFuture<ShipmentRouteSummary> result) {
        return new StreamObserver<>() {
            @Override
            public void onNext(ShipmentRouteSummary summary) {
                result.complete(summary);
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private static Status.Code statusOf(CompletableFuture<?> result) throws Exception {
        try {
            result.get(10, TimeUnit.SECONDS);
            return Status.Code.OK;
        } catch (java.util.concurrent.ExecutionException ex) {
            return Status.fromThrowable(ex.getCause()).getCode();
        }
    }
}
