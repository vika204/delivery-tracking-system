package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.service.CourierLiveLocationService;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.ShipmentRouteService;
import com.delivery.tracking.service.TrackingService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Set;

@GrpcService
public class ShipmentTrackingGrpcService extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingGrpcService.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of("DELIVERED", "CANCELLED");

    private final LocationStreamPublisher publisher;
    private final TrackingService trackingService;
    private final LocationService locationService;
    private final ShipmentRouteService routeService;
    private final CourierLiveLocationService liveLocationService;
    private final TrackingProtoMapper mapper;

    public ShipmentTrackingGrpcService(LocationStreamPublisher publisher,
                                       TrackingService trackingService,
                                       LocationService locationService,
                                       ShipmentRouteService routeService,
                                       CourierLiveLocationService liveLocationService,
                                       TrackingProtoMapper mapper) {
        this.publisher = publisher;
        this.trackingService = trackingService;
        this.locationService = locationService;
        this.routeService = routeService;
        this.liveLocationService = liveLocationService;
        this.mapper = mapper;
    }

    @Override
    public void subscribeToShipmentLocations(SubscribeToShipmentLocationsRequest request,
                                             StreamObserver<ShipmentLocationUpdate> responseObserver) {
        long shipmentId = request.getShipmentId();
        if (shipmentId <= 0) {
            responseObserver.onError(invalidShipmentId());
            return;
        }
        publisher.subscribe(shipmentId, (ServerCallStreamObserver<ShipmentLocationUpdate>) responseObserver);

        try {
            if (TERMINAL_STATUSES.contains(trackingService.getShipmentById(shipmentId).status())) {
                publisher.completeShipment(shipmentId);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not check shipment status; keeping gRPC stream open: shipmentId={}", shipmentId, ex);
        }
    }

    @Override
    public void getShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest request,
                                        StreamObserver<ShipmentDeliveryInfo> responseObserver) {
        long shipmentId = request.getShipmentId();
        if (shipmentId <= 0) {
            responseObserver.onError(invalidShipmentId());
            return;
        }
        try {
            ShipmentDto shipment = trackingService.getShipmentById(shipmentId);
            if (TrackingService.UNAVAILABLE_STATUS.equals(shipment.status())) {
                throw Status.UNAVAILABLE.withDescription("shipment-service is temporarily unavailable").asRuntimeException();
            }
            responseObserver.onNext(mapper.toDeliveryInfo(shipmentId, shipment.status(),
                    locationService.findLatestLocation(shipmentId)));
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            log.warn("Delivery info lookup failed: shipmentId={}", shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        }
    }

    @Override
    public StreamObserver<ShipmentRoutePoint> recordShipmentRoute(StreamObserver<ShipmentRouteSummary> responseObserver) {
        return new RouteUploadObserver(responseObserver, routeService, mapper);
    }

    @Override
    public StreamObserver<CourierLocationReport> streamCourierLocations(StreamObserver<CourierLocationDecision> responseObserver) {
        return new CourierLocationStreamObserver(responseObserver, liveLocationService, mapper);
    }

    private static RuntimeException invalidShipmentId() {
        return Status.INVALID_ARGUMENT.withDescription("shipmentId must be positive").asRuntimeException();
    }
}
