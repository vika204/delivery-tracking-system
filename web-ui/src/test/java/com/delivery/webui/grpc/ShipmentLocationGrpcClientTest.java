package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentLocationGrpcClientTest {

    @Mock
    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    @Mock
    private StreamObserver<ShipmentLocationUpdate> observer;

    @Test
    void subscribesUsingRequestedShipmentIdAndProvidedObserver() {
        ArgumentCaptor<SubscribeToShipmentLocationsRequest> requestCaptor =
                ArgumentCaptor.forClass(SubscribeToShipmentLocationsRequest.class);
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        client.subscribe(15, observer);

        verify(stub).subscribeToShipmentLocations(
                requestCaptor.capture(),
                same(observer)
        );
        assertThat(requestCaptor.getValue().getShipmentId()).isEqualTo(15);
    }

    @Test
    void returnsGrpcContextThatCancelsTheStream() {
        Context[] callContext = new Context[1];
        doAnswer(invocation -> {
            callContext[0] = Context.current();
            return null;
        }).when(stub).subscribeToShipmentLocations(any(), any());
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        Context.CancellableContext context = client.subscribe(15, observer);

        assertThat(callContext[0]).isNotNull();
        assertThat(context.isCancelled()).isFalse();
        assertThat(callContext[0].isCancelled()).isFalse();

        context.cancel(null);

        assertThat(context.isCancelled()).isTrue();
        assertThat(callContext[0].isCancelled()).isTrue();
    }

    @Test
    void cancelsContextIfStubThrowsWhileOpeningStream() {
        Context[] callContext = new Context[1];
        doAnswer(invocation -> {
            callContext[0] = Context.current();
            throw Status.UNAVAILABLE.asRuntimeException();
        }).when(stub).subscribeToShipmentLocations(any(), any());
        ShipmentLocationGrpcClient client =
                new ShipmentLocationGrpcClient(stub);

        assertThatThrownBy(() -> client.subscribe(15, observer))
                .isInstanceOf(RuntimeException.class);
        assertThat(callContext[0].isCancelled()).isTrue();
    }
}
