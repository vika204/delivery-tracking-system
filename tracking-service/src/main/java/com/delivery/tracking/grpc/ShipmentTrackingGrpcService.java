package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.route.ShipmentRouteService;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Optional;

@GrpcService
public class ShipmentTrackingGrpcService extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingGrpcService.class);
    private final LocationStreamPublisher publisher;
    private final TrackingService trackingService;
    private final LocationService locationService;
    private final ShipmentRouteService routeService;
    private final TrackingProtoMapper mapper;

    public ShipmentTrackingGrpcService(
            LocationStreamPublisher publisher,
            TrackingService trackingService,
            LocationService locationService,
            ShipmentRouteService routeService,
            TrackingProtoMapper mapper
    ) {
        this.publisher = publisher;
        this.trackingService = trackingService;
        this.locationService = locationService;
        this.routeService = routeService;
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
    public void getLatestShipmentLocation(GetLatestShipmentLocationRequest request, StreamObserver<ShipmentLocationUpdate> responseObserver) {
        long shipmentId = request.getShipmentId();

        if (shipmentId <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("shipmentId must be positive").asRuntimeException());
            return;
        }

        log.info("Received gRPC latest location request: shipmentId={}", shipmentId);

        Optional<Location> latest;
        try {
            latest = locationService.findLatestLocation(shipmentId);
        } catch (RuntimeException ex) {
            log.error("Latest location lookup failed: shipmentId={}", shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
            return;
        }

        if (latest.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("no locations for shipment " + shipmentId).asRuntimeException());
            return;
        }

        responseObserver.onNext(mapper.toProto(latest.get()));
        responseObserver.onCompleted();
    }

    // Client streaming
    @Override
    public StreamObserver<ShipmentRoutePoint> recordShipmentRoute(StreamObserver<ShipmentRouteSummary> responseObserver) {
        return new RouteUploadObserver(responseObserver, routeService, mapper);
    }
}
