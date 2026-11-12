package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentLocationGrpcClientTest {

    @Mock
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    @Mock
    private ShipmentLocationGrpcClient.Listener listener;

    @Test
    void subscribesUsingRequestedShipmentIdAndForwardsUpdates() {
        ArgumentCaptor<SubscribeToShipmentLocationsRequest> requestCaptor =
                ArgumentCaptor.forClass(SubscribeToShipmentLocationsRequest.class);
        ArgumentCaptor<StreamObserver<ShipmentLocationUpdate>> observerCaptor =
                streamObserverCaptor();
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);
        ShipmentLocationUpdate update = ShipmentLocationUpdate.newBuilder()
                .setLocationId(11)
                .setShipmentId(15)
                .setLatitude(50.455)
                .setLongitude(30.53)
                .build();

        client.subscribe(15, listener);

        verify(stub).subscribeToShipmentLocations(
                requestCaptor.capture(),
                observerCaptor.capture()
        );
        assertThat(requestCaptor.getValue().getShipmentId()).isEqualTo(15);

        observerCaptor.getValue().onNext(update);

        verify(listener).onNext(update);
    }

    @Test
    void forwardsGrpcCompletionAndError() {
        ArgumentCaptor<StreamObserver<ShipmentLocationUpdate>> observerCaptor =
                streamObserverCaptor();
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        client.subscribe(15, listener);
        verify(stub).subscribeToShipmentLocations(
                any(),
                observerCaptor.capture()
        );

        RuntimeException error = Status.UNAVAILABLE.asRuntimeException();
        observerCaptor.getValue().onError(error);
        observerCaptor.getValue().onCompleted();

        verify(listener).onError(error);
        verify(listener).onCompleted();
    }

    @Test
    void cancellationStopsFurtherCallbacks() {
        ArgumentCaptor<StreamObserver<ShipmentLocationUpdate>> observerCaptor =
                streamObserverCaptor();
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        ShipmentLocationGrpcClient.Subscription subscription =
                client.subscribe(15, listener);
        verify(stub).subscribeToShipmentLocations(
                any(),
                observerCaptor.capture()
        );

        subscription.cancel();
        observerCaptor.getValue().onNext(
                ShipmentLocationUpdate.getDefaultInstance()
        );
        observerCaptor.getValue().onError(
                Status.CANCELLED.asRuntimeException()
        );
        observerCaptor.getValue().onCompleted();

        verify(listener, never()).onNext(any());
        verify(listener, never()).onError(any());
        verify(listener, never()).onCompleted();
    }

    @Test
    void cancelsContextIfStubThrowsWhileOpeningStream() {
        doAnswer(invocation -> {
            throw Status.UNAVAILABLE.asRuntimeException();
        }).when(stub).subscribeToShipmentLocations(any(), any());
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.subscribe(15, listener)
                )
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<StreamObserver<ShipmentLocationUpdate>>
    streamObserverCaptor() {
        return ArgumentCaptor.forClass(StreamObserver.class);
    }
}
