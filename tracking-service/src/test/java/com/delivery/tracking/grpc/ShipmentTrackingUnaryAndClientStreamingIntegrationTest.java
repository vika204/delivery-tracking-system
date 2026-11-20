package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.entity.RouteUpload;
import com.delivery.tracking.repository.RouteUploadRepository;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

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

    @MockitoSpyBean
    private RouteUploadRepository routeUploadRepository;

    @MockitoBean
    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        routeUploadRepository.deleteAll();
        trackingRepository.deleteAll();
        when(trackingService.getShipmentById(anyLong()))
                .thenAnswer(invocation -> new ShipmentDto(invocation.getArgument(0), "Recipient", "IN_TRANSIT"));
        when(trackingService.getShipmentByIdWithoutFallback(anyLong()))
                .thenAnswer(invocation -> new ShipmentDto(invocation.getArgument(0), "Recipient", "IN_TRANSIT"));
    }


    @Test
    void unaryReturnsStatusAndLatestLocation() {
        save(15L, "50.100000", "30.100000", LocalDateTime.of(2026, 11, 15, 10, 0));
        save(15L, "50.300000", "30.300000", LocalDateTime.of(2026, 11, 15, 10, 30));
        save(15L, "50.200000", "30.200000", LocalDateTime.of(2026, 11, 15, 10, 15));
        save(20L, "49.000000", "28.000000", LocalDateTime.of(2026, 11, 15, 11, 0));

        ShipmentDeliveryInfo info = blockingStub.getShipmentDeliveryInfo(request(15L));

        assertThat(info.getShipmentId()).isEqualTo(15L);
        assertThat(info.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(info.getHasLocation()).isTrue();
        assertThat(info.getLatitude()).isCloseTo(50.3, within(0.000001));
        assertThat(info.getLongitude()).isCloseTo(30.3, within(0.000001));
        assertThat(info.getRecordedAt().getSeconds()).isEqualTo(Instant.parse("2026-11-15T10:30:00Z").getEpochSecond());
    }

    @Test
    void unaryReturnsStatusWithoutLocationWhenNoCoordinatesYet() {
        when(trackingService.getShipmentByIdWithoutFallback(16L)).thenReturn(new ShipmentDto(16L, "WAITING_FOR_COURIER", "WAITING_FOR_COURIER"));

        ShipmentDeliveryInfo info = blockingStub.getShipmentDeliveryInfo(request(16L));

        assertThat(info.getStatus()).isEqualTo("WAITING_FOR_COURIER");
        assertThat(info.getHasLocation()).isFalse();
        assertThat(info.hasRecordedAt()).isFalse();
    }

    @Test
    void unaryReturnsNotFoundWhenShipmentDoesNotExist() {
        when(trackingService.getShipmentByIdWithoutFallback(99L))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThat(statusOf(() -> blockingStub.getShipmentDeliveryInfo(request(99L))))
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void unaryReturnsUnavailableWhenShipmentServiceIsDown() {
        when(trackingService.getShipmentByIdWithoutFallback(15L))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(statusOf(() -> blockingStub.getShipmentDeliveryInfo(request(15L))))
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void unaryReturnsInvalidArgumentForInvalidShipmentId() {
        assertThat(statusOf(() -> blockingStub.getShipmentDeliveryInfo(request(0L))))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }


    @Test
    void clientStreamingSavesRouteAndReturnsCounts() throws Exception {
        ShipmentRouteSummary summary = upload(List.of(
                point("upload-1", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-1", 15L, 50.4510, 30.5240, "2026-11-15T10:00:10Z"),
                point("upload-1", 15L, 50.4520, 30.5250, "2026-11-15T10:00:20Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(summary.getUploadId()).isEqualTo("upload-1");
        assertThat(summary.getShipmentId()).isEqualTo(15L);
        assertThat(summary.getReceivedCount()).isEqualTo(3);
        assertThat(summary.getSavedCount()).isEqualTo(3);
        assertThat(summary.getRejectedCount()).isZero();
        assertThat(summary.getDuplicate()).isFalse();
        assertThat(summary.getLastLocationId()).isGreaterThan(summary.getFirstLocationId());

        assertThat(trackingRepository.count()).isEqualTo(3);
        assertThat(routeUploadRepository.findById("upload-1")).isPresent();

        ShipmentDeliveryInfo info = blockingStub.getShipmentDeliveryInfo(request(15L));
        assertThat(info.getLatitude()).isCloseTo(50.4520, within(0.000001));
    }

    @Test
    void clientStreamingRejectsInvalidPointsAndSavesValidOnes() throws Exception {
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        ShipmentRouteSummary summary = upload(List.of(
                point("upload-mixed", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-mixed", 15L, 95.0, 30.5240, "2026-11-15T10:00:10Z"),
                point("upload-mixed", 15L, 50.4510, 200.0, "2026-11-15T10:00:20Z"),
                point("upload-mixed", 15L, Double.NaN, 30.52, "2026-11-15T10:00:30Z"),
                pointWithoutTime("upload-mixed", 15L, 50.4515, 30.5245),
                point("upload-mixed", 15L, 50.4520, 30.5250, future),
                point("upload-mixed", 15L, 50.4530, 30.5260, "2026-11-15T10:00:40Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(summary.getReceivedCount()).isEqualTo(7);
        assertThat(summary.getSavedCount()).isEqualTo(2);
        assertThat(summary.getRejectedCount()).isEqualTo(5);
        assertThat(trackingRepository.count()).isEqualTo(2);
    }

    @Test
    void clientStreamingWithOnlyInvalidPointsSavesNothingButReturnsSummary() throws Exception {
        ShipmentRouteSummary summary = upload(List.of(
                point("upload-bad", 15L, 95.0, 30.52, "2026-11-15T10:00:00Z"),
                point("upload-bad", 15L, 50.45, 190.0, "2026-11-15T10:00:10Z")
        )).get(10, TimeUnit.SECONDS);

        assertThat(summary.getReceivedCount()).isEqualTo(2);
        assertThat(summary.getSavedCount()).isZero();
        assertThat(summary.getRejectedCount()).isEqualTo(2);
        assertThat(summary.getFirstLocationId()).isZero();
        assertThat(trackingRepository.count()).isZero();
    }

    @Test
    void clientStreamingRetryWithSameUploadIdDoesNotDuplicatePoints() throws Exception {
        List<ShipmentRoutePoint> route = List.of(
                point("upload-retry", 15L, 50.4501, 30.5234, "2026-11-15T10:00:00Z"),
                point("upload-retry", 15L, 95.0, 30.5240, "2026-11-15T10:00:10Z"),
                point("upload-retry", 15L, 50.4510, 30.5240, "2026-11-15T10:00:20Z")
        );

        ShipmentRouteSummary first = upload(route).get(10, TimeUnit.SECONDS);
        ShipmentRouteSummary second = upload(route).get(10, TimeUnit.SECONDS);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(second.getDuplicate()).isTrue();
        assertThat(second.getReceivedCount()).isEqualTo(3);
        assertThat(second.getSavedCount()).isEqualTo(2);
        assertThat(second.getRejectedCount()).isEqualTo(1);
        assertThat(second.getFirstLocationId()).isEqualTo(first.getFirstLocationId());
        assertThat(second.getLastLocationId()).isEqualTo(first.getLastLocationId());
        assertThat(trackingRepository.count()).isEqualTo(2);
    }

    @Test
    void clientStreamingConcurrentRetryWithSameUploadIdDoesNotDuplicatePoints() throws Exception {
        List<ShipmentRoutePoint> route = List.of(point("upload-race", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"));
        ShipmentRouteSummary first = upload(route).get(10, TimeUnit.SECONDS);
        RouteUpload committed = routeUploadRepository.findById("upload-race").orElseThrow();

        doReturn(Optional.empty()).doReturn(Optional.of(committed))
                .when(routeUploadRepository).findById("upload-race");

        ShipmentRouteSummary second = upload(route).get(10, TimeUnit.SECONDS);

        assertThat(second.getDuplicate()).isTrue();
        assertThat(second.getFirstLocationId()).isEqualTo(first.getFirstLocationId());
        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    @Test
    void clientStreamingRejectsUploadIdReusedForAnotherShipment() throws Exception {
        upload(List.of(point("upload-x", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"))).get(10, TimeUnit.SECONDS);

        assertThat(statusOf(upload(List.of(point("upload-x", 16L, 50.45, 30.52, "2026-11-15T10:00:00Z")))))
                .isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    @Test
    void clientStreamingRejectsMixedShipmentIdsAsProtocolError() throws Exception {
        CompletableFuture<ShipmentRouteSummary> result = upload(List.of(
                point("upload-ids", 15L, 50.45, 30.52, "2026-11-15T10:00:00Z"),
                point("upload-ids", 16L, 50.46, 30.53, "2026-11-15T10:00:10Z")
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
        context.cancel(null);

        assertThat(statusOf(result)).isEqualTo(Status.Code.CANCELLED);
        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(trackingRepository.count()).isZero();
            assertThat(routeUploadRepository.count()).isZero();
        });
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


    private Location save(long shipmentId, String latitude, String longitude, LocalDateTime recordedAt) {
        return locationService.createLocation(new Location(
                null, shipmentId, new BigDecimal(latitude), new BigDecimal(longitude), recordedAt));
    }

    private static GetShipmentDeliveryInfoRequest request(long shipmentId) {
        return GetShipmentDeliveryInfoRequest.newBuilder().setShipmentId(shipmentId).build();
    }

    private static ShipmentRoutePoint point(String uploadId, long shipmentId, double lat, double lon, String recordedAt) {
        Instant instant = Instant.parse(recordedAt);
        return pointWithoutTime(uploadId, shipmentId, lat, lon).toBuilder()
                .setRecordedAt(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
                .build();
    }

    private static ShipmentRoutePoint pointWithoutTime(String uploadId, long shipmentId, double lat, double lon) {
        return ShipmentRoutePoint.newBuilder()
                .setUploadId(uploadId)
                .setShipmentId(shipmentId)
                .setLatitude(lat)
                .setLongitude(lon)
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

    private static Status.Code statusOf(Runnable call) {
        try {
            call.run();
            return Status.Code.OK;
        } catch (StatusRuntimeException ex) {
            return ex.getStatus().getCode();
        }
    }

    private static Status.Code statusOf(CompletableFuture<?> result) throws Exception {
        try {
            result.get(10, TimeUnit.SECONDS);
            return Status.Code.OK;
        } catch (ExecutionException ex) {
            return Status.fromThrowable(ex.getCause()).getCode();
        }
    }
}
