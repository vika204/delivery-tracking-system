package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;

@GrpcService
public class ShipmentTrackingGrpcService extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingGrpcService.class);
    private final LocationStreamPublisher publisher;
    // Статус, який повертає fallback TrackingService, коли shipment-service недоступний.
    static final String SHIPMENT_STATUS_UNAVAILABLE = "UNAVAILABLE";

    private final TrackingService trackingService;
    private final LocationService locationService;
    private final TrackingProtoMapper mapper;

    public ShipmentTrackingGrpcService(
            LocationStreamPublisher publisher,
            TrackingService trackingService,
            LocationService locationService,
            TrackingProtoMapper mapper
    ) {
        this.publisher = publisher;
        this.trackingService = trackingService;
        this.locationService = locationService;
        this.mapper = mapper;
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

    // Unary
    @Override
    public void getShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest request, StreamObserver<ShipmentDeliveryInfo> responseObserver) {
        long shipmentId = request.getShipmentId();

        if (shipmentId <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("shipmentId must be positive").asRuntimeException());
            return;
        }

        log.info("Received gRPC delivery info request: shipmentId={}", shipmentId);

        ShipmentDto shipment;
        try {
            shipment = trackingService.getShipmentById(shipmentId);
        } catch (HttpClientErrorException.NotFound ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("shipment " + shipmentId + " not found").asRuntimeException());
            return;
        } catch (HttpClientErrorException ex) {
            log.warn("Shipment-service rejected status request: shipmentId={}, status={}", shipmentId, ex.getStatusCode());
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("shipment-service rejected request: " + ex.getStatusCode())
                    .asRuntimeException());
            return;
        } catch (RuntimeException ex) {
            log.error("Shipment status lookup failed: shipmentId={}", shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
            return;
        }

        if (shipment == null || shipment.status() == null || SHIPMENT_STATUS_UNAVAILABLE.equals(shipment.status())) {
            // Fallback TrackingService: shipment-service недоступний -> повторюваний статус для клієнта.
            log.warn("Shipment status unavailable (fallback): shipmentId={}", shipmentId);
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("shipment-service is temporarily unavailable")
                    .asRuntimeException());
            return;
        }

        Optional<Location> latest;
        try {
            latest = locationService.findLatestLocation(shipmentId);
        } catch (RuntimeException ex) {
            log.error("Latest location lookup failed: shipmentId={}", shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
            return;
        }

        responseObserver.onNext(mapper.toDeliveryInfo(shipmentId, shipment.status(), latest));
        responseObserver.onCompleted();
    }
}
