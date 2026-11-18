package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.service.CourierLiveLocationService;
import com.delivery.tracking.service.ShipmentRouteService;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.util.Optional;

@GrpcService
public class ShipmentTrackingGrpcService extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingGrpcService.class);
    private final LocationStreamPublisher publisher;
    static final String SHIPMENT_STATUS_UNAVAILABLE = "UNAVAILABLE";

    private final TrackingService trackingService;
    private final LocationService locationService;
    private final ShipmentRouteService routeService;
    private final CourierLiveLocationService liveLocationService;
    private final TrackingProtoMapper mapper;
    private final Clock clock;

    public ShipmentTrackingGrpcService(
            LocationStreamPublisher publisher,
            TrackingService trackingService,
            LocationService locationService,
            ShipmentRouteService routeService,
            TrackingProtoMapper mapper
    ) {
        this(publisher, trackingService, locationService, routeService, null, mapper);
    }

    @Autowired
    public ShipmentTrackingGrpcService(
            LocationStreamPublisher publisher,
            TrackingService trackingService,
            LocationService locationService,
            ShipmentRouteService routeService,
            CourierLiveLocationService liveLocationService,
            TrackingProtoMapper mapper
    ) {
        this.publisher = publisher;
        this.trackingService = trackingService;
        this.locationService = locationService;
        this.routeService = routeService;
        this.liveLocationService = liveLocationService;
        this.mapper = mapper;
        this.clock = Clock.systemUTC();
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

    @Override
    public StreamObserver<ShipmentRoutePoint> recordShipmentRoute(StreamObserver<ShipmentRouteSummary> responseObserver) {
        return new RouteUploadObserver(responseObserver, routeService, mapper, clock);
    }

    @Override
    public StreamObserver<CourierLocationReport> streamCourierLocations(StreamObserver<CourierLocationDecision> responseObserver) {
        return new CourierLocationStreamObserver(responseObserver, liveLocationService, mapper, clock);
    }
}
