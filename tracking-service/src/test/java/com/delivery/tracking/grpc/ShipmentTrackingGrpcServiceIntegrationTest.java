package com.delivery.tracking.grpc;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.LocationService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class)
@Testcontainers
class ShipmentTrackingGrpcServiceIntegrationTest {

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
    }

    @Autowired
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    @Autowired
    private LocationService locationService;

    @Autowired
    private TrackingRepository trackingRepository;

    @MockitoSpyBean
    private LocationStreamPublisher publisher;

    private final List<Context.CancellableContext> contexts = new ArrayList<>();
    private final BlockingQueue<Throwable> streamErrors = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        trackingRepository.deleteAll();
        streamErrors.clear();
    }

    @AfterEach
    void tearDown() {
        for (Context.CancellableContext context : contexts) {
            context.cancel(null);
        }
        contexts.clear();
    }

    @Test
    void shouldReceiveLocationAfterSavingToDatabase() throws Exception {
        BlockingQueue<ShipmentLocationUpdate> received = new LinkedBlockingQueue<>();

        subscribe(15L, received);

        LocalDateTime recordedAt = LocalDateTime.of(2026, 11, 9, 14, 30);

        Location saved = locationService.createLocation(new Location(
                null,
                15L,
                new BigDecimal("50.4501"),
                new BigDecimal("30.5234"),
                recordedAt
        ));

        ShipmentLocationUpdate update = received.poll(5, TimeUnit.SECONDS);

        assertThat(update).isNotNull();
        assertThat(update.getLocationId()).isEqualTo(saved.getLocationId());
        assertThat(update.getShipmentId()).isEqualTo(15L);
        assertThat(update.getLatitude()).isCloseTo(50.4501, within(0.000001));
        assertThat(update.getLongitude()).isCloseTo(30.5234, within(0.000001));
        assertThat(update.getRecordedAt().getSeconds()).isEqualTo(recordedAt.toEpochSecond(ZoneOffset.UTC));
        assertThat(update.getRecordedAt().getNanos()).isZero();

        assertThat(trackingRepository.findById(saved.getLocationId())).isPresent();
        assertThat(streamErrors).isEmpty();
    }

    @Test
    void shouldReceiveOnlySubscribedShipmentLocations() throws Exception {
        BlockingQueue<ShipmentLocationUpdate> received = new LinkedBlockingQueue<>();

        subscribe(15L, received);

        locationService.createLocation(new Location(
                null,
                20L,
                new BigDecimal("49.0000"),
                new BigDecimal("28.0000"),
                LocalDateTime.of(2026, 11, 9, 14, 30)
        ));

        assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();

        locationService.createLocation(new Location(
                null,
                15L,
                new BigDecimal("50.4501"),
                new BigDecimal("30.5234"),
                LocalDateTime.of(2026, 11, 9, 14, 31)
        ));

        ShipmentLocationUpdate update = received.poll(5, TimeUnit.SECONDS);

        assertThat(update).isNotNull();
        assertThat(update.getShipmentId()).isEqualTo(15L);
        assertThat(update.getLatitude()).isCloseTo(50.4501, within(0.000001));
        assertThat(streamErrors).isEmpty();
    }

    @Test
    void shouldReturnInvalidArgumentForInvalidShipmentId() throws Exception {
        CompletableFuture<Status.Code> result = new CompletableFuture<>();

        SubscribeToShipmentLocationsRequest request =
                SubscribeToShipmentLocationsRequest.newBuilder()
                        .setShipmentId(0)
                        .build();

        stub.subscribeToShipmentLocations(request, new StreamObserver<>() {

            @Override
            public void onNext(ShipmentLocationUpdate update) {
            }

            @Override
            public void onError(Throwable error) {
                result.complete(Status.fromThrowable(error).getCode());
            }

            @Override
            public void onCompleted() {
                result.complete(Status.Code.OK);
            }
        });

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private void subscribe(long shipmentId, BlockingQueue<ShipmentLocationUpdate> received) throws Exception {
        CountDownLatch registered = new CountDownLatch(1);

        doAnswer(invocation -> {
            invocation.callRealMethod();
            registered.countDown();
            return null;
        }).when(publisher).subscribe(eq(shipmentId), any());

        Context.CancellableContext context = Context.current().withCancellation();
        contexts.add(context);

        SubscribeToShipmentLocationsRequest request =
                SubscribeToShipmentLocationsRequest.newBuilder()
                        .setShipmentId(shipmentId)
                        .build();

        context.run(() -> stub.subscribeToShipmentLocations(request, new StreamObserver<>() {

            @Override
            public void onNext(ShipmentLocationUpdate update) {
                received.add(update);
            }

            @Override
            public void onError(Throwable error) {
                streamErrors.add(error);
            }

            @Override
            public void onCompleted() {
            }
        }));

        assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();
    }
}