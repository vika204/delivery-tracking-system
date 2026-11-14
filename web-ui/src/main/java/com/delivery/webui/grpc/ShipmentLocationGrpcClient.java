package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class ShipmentLocationGrpcClient {

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    public ShipmentLocationGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub) {
        this.stub = stub;
    }

    public Context.CancellableContext subscribe(long shipmentId, StreamObserver<ShipmentLocationUpdate> observer) {
        if (shipmentId <= 0) {
            throw new IllegalArgumentException("shipmentId must be positive");
        }

        Objects.requireNonNull(observer, "observer must not be null");

        SubscribeToShipmentLocationsRequest request = SubscribeToShipmentLocationsRequest.newBuilder()
                .setShipmentId(shipmentId)
                .build();
        Context.CancellableContext context = Context.current().withCancellation();

        try {
            context.run(() -> stub.subscribeToShipmentLocations(request, observer));
        } catch (RuntimeException ex) {
            context.cancel(ex);
            throw ex;
        }

        return context;
    }
}
