package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.service.TrackingService;
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
    private final TrackingService trackingService;

    public ShipmentTrackingGrpcService(LocationStreamPublisher publisher, TrackingService trackingService) {
        this.publisher = publisher;
        this.trackingService = trackingService;
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

        try {
            ShipmentDto shipment = trackingService.getShipmentById(shipmentId);
            if ("DELIVERED".equals(shipment.status()) || "CANCELLED".equals(shipment.status())) {
                publisher.completeShipment(shipmentId);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not check shipment status; keeping gRPC stream open: shipmentId={}", shipmentId, ex);
        }
    }
}