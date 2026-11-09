package com.delivery.tracking.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ShipmentTrackingGrpcService extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingGrpcService.class);
    private final LocationStreamPublisher publisher;

    public ShipmentTrackingGrpcService(LocationStreamPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void subscribeToShipmentLocations(SubscribeToShipmentLocationsRequest request, StreamObserver<ShipmentLocationUpdate> responseObserver) {
        long shipmentId = request.getShipmentId();

        if (shipmentId <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("shipmentId must be positive").asRuntimeException());
            return;
        }
        log.info("Received gRPC subscription request: shipmentId={}", shipmentId);
        ServerCallStreamObserver<ShipmentLocationUpdate> serverObserver = (ServerCallStreamObserver<ShipmentLocationUpdate>) responseObserver;
        publisher.subscribe(shipmentId, serverObserver);
    }
}