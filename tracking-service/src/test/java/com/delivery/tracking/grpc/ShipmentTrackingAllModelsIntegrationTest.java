package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.repository.RouteUploadRepository;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.TrackingService;
import com.google.protobuf.Timestamp;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = {
        ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class,
        ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub.class
})
@Testcontainers
class ShipmentTrackingAllModelsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_all_models_test_db")
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
    private TrackingRepository trackingRepository;

    @Autowired
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
    void allFourGrpcModelsWorkTogetherInSingleService() throws Exception {
        long shipmentId = 42L;
        BlockingQueue<ShipmentLocationUpdate> liveUpdates = new LinkedBlockingQueue<>();
        Context.CancellableContext subscriptionContext = Context.current().withCancellation();

        try {
            subscriptionContext.run(() -> asyncStub.subscribeToShipmentLocations(
                    SubscribeToShipmentLocationsRequest.newBuilder().setShipmentId(shipmentId).build(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(ShipmentLocationUpdate update) {
                            liveUpdates.add(update);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }

                        @Override
                        public void onCompleted() {
                        }
                    }
            ));

            Thread.sleep(150);
            ShipmentRouteSummary routeSummary = uploadRoute(List.of(
                    routePoint("route-42", shipmentId, 50.4500, 30.5200, "2026-11-15T10:00:00Z"),
                    routePoint("route-42", shipmentId, 50.4510, 30.5210, "2026-11-15T10:00:10Z")
            )).get(10, TimeUnit.SECONDS);

            assertThat(routeSummary.getSavedCount()).isEqualTo(2);

            List<CourierLocationDecision> bidiDecisions = streamLive(List.of(
                    report(shipmentId, 50.4520, 30.5220, "2026-11-15T10:00:25Z"),
                    report(shipmentId, 48.0000, 25.0000, "2026-11-15T10:00:30Z")
            )).get(10, TimeUnit.SECONDS);

            assertThat(bidiDecisions).hasSize(2);
            assertThat(bidiDecisions.get(0).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED);
            assertThat(bidiDecisions.get(1).getStatus()).isEqualTo(CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP);

            ShipmentLocationUpdate update1 = liveUpdates.poll(5, TimeUnit.SECONDS);
            ShipmentLocationUpdate update2 = liveUpdates.poll(5, TimeUnit.SECONDS);
            ShipmentLocationUpdate update3 = liveUpdates.poll(5, TimeUnit.SECONDS);

            assertThat(update1).isNotNull();
            assertThat(update2).isNotNull();
            assertThat(update3).isNotNull();
            assertThat(update3.getLatitude()).isCloseTo(50.4520, within(0.000001));

            ShipmentDeliveryInfo deliveryInfo = blockingStub.getShipmentDeliveryInfo(
                    GetShipmentDeliveryInfoRequest.newBuilder().setShipmentId(shipmentId).build()
            );

            assertThat(deliveryInfo.getShipmentId()).isEqualTo(shipmentId);
            assertThat(deliveryInfo.getStatus()).isEqualTo("IN_TRANSIT");
            assertThat(deliveryInfo.getHasLocation()).isTrue();
            assertThat(deliveryInfo.getLatitude()).isCloseTo(50.4520, within(0.000001));
            assertThat(deliveryInfo.getLongitude()).isCloseTo(30.5220, within(0.000001));
        } finally {
            subscriptionContext.cancel(null);
        }
    }

    private static CourierLocationReport report(long shipmentId, double lat, double lon, String recordedAt) {
        Instant instant = Instant.parse(recordedAt);
        return CourierLocationReport.newBuilder()
                .setShipmentId(shipmentId)
                .setLatitude(lat)
                .setLongitude(lon)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
                .build();
    }

    private static ShipmentRoutePoint routePoint(String uploadId, long shipmentId, double lat, double lon, String recordedAt) {
        Instant instant = Instant.parse(recordedAt);
        return ShipmentRoutePoint.newBuilder()
                .setUploadId(uploadId)
                .setShipmentId(shipmentId)
                .setLatitude(lat)
                .setLongitude(lon)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
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

    private CompletableFuture<ShipmentRouteSummary> uploadRoute(List<ShipmentRoutePoint> points) {
        CompletableFuture<ShipmentRouteSummary> result = new CompletableFuture<>();
        StreamObserver<ShipmentRoutePoint> requestObserver = asyncStub.recordShipmentRoute(new StreamObserver<>() {
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
        });
        points.forEach(requestObserver::onNext);
        requestObserver.onCompleted();
        return result;
    }
}
