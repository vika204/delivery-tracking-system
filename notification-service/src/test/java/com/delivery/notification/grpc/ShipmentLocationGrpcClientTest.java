package com.delivery.notification.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentLocationGrpcClientTest {

    @Mock
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    private ShipmentLocationGrpcClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void shouldRetryWhenServerIsUnavailable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch reconnected = new CountDownLatch(1);

        doAnswer(invocation -> {
            StreamObserver<ShipmentLocationUpdate> observer = invocation.getArgument(1);

            int attempt = attempts.incrementAndGet();

            if (attempt == 1) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                reconnected.countDown();
            }

            return null;
        }).when(stub).subscribeToShipmentLocations(any(), any());

        client = new ShipmentLocationGrpcClient(stub, 10, 100, 2.0);

        assertThat(client.subscribe(15L)).isTrue();

        assertThat(reconnected.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts.get()).isEqualTo(2);

        assertThat(client.unsubscribe(15L)).isTrue();
    }

    @Test
    void shouldReconnectWhenActiveStreamFails() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<StreamObserver<ShipmentLocationUpdate>> currentObserver = new AtomicReference<>();
        CountDownLatch reconnected = new CountDownLatch(1);

        doAnswer(invocation -> {
            StreamObserver<ShipmentLocationUpdate> observer = invocation.getArgument(1);

            currentObserver.set(observer);

            if (attempts.incrementAndGet() == 2) {
                reconnected.countDown();
            }

            return null;
        }).when(stub).subscribeToShipmentLocations(any(), any());

        client = new ShipmentLocationGrpcClient(stub, 10, 100, 2.0);

        assertThat(client.subscribe(15L)).isTrue();
        assertThat(attempts.get()).isEqualTo(1);

        currentObserver.get().onNext(ShipmentLocationUpdate.newBuilder()
                .setShipmentId(15L)
                .setLocationId(1L)
                .setLatitude(50.4501)
                .setLongitude(30.5234)
                .build());

        currentObserver.get().onError(Status.UNAVAILABLE.asRuntimeException());

        assertThat(reconnected.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts.get()).isEqualTo(2);

        assertThat(client.unsubscribe(15L)).isTrue();
    }

    @Test
    void shouldNotRetryInvalidArgument() {
        doAnswer(invocation -> {
            StreamObserver<ShipmentLocationUpdate> observer = invocation.getArgument(1);

            observer.onError(Status.INVALID_ARGUMENT.asRuntimeException());

            return null;
        }).when(stub).subscribeToShipmentLocations(any(), any());

        client = new ShipmentLocationGrpcClient(stub, 10, 100, 2.0);

        assertThat(client.subscribe(15L)).isTrue();

        verify(stub, after(200).times(1))
                .subscribeToShipmentLocations(any(SubscribeToShipmentLocationsRequest.class), any());

        assertThat(client.unsubscribe(15L)).isFalse();
    }

    @Test
    void shouldCancelScheduledRetryOnUnsubscribe() {
        doAnswer(invocation -> {
            StreamObserver<ShipmentLocationUpdate> observer = invocation.getArgument(1);

            observer.onError(Status.UNAVAILABLE.asRuntimeException());

            return null;
        }).when(stub).subscribeToShipmentLocations(any(), any());

        client = new ShipmentLocationGrpcClient(stub, 200, 200, 2.0);

        assertThat(client.subscribe(15L)).isTrue();
        assertThat(client.unsubscribe(15L)).isTrue();

        verify(stub, after(350).times(1))
                .subscribeToShipmentLocations(any(SubscribeToShipmentLocationsRequest.class), any());
    }
}