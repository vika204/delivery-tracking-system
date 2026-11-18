package com.delivery.dispatch.service;

import com.delivery.dispatch.dto.LiveLocationStreamResponse;
import com.delivery.dispatch.dto.LocationPointRequest;
import com.delivery.dispatch.dto.RouteUploadResponse;
import com.delivery.dispatch.grpc.TrackingGrpcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CourierTrackingService {

    private final TrackingGrpcClient trackingClient;

    public CourierTrackingService(TrackingGrpcClient trackingClient) {
        this.trackingClient = trackingClient;
    }

    public RouteUploadResponse uploadRoute(long shipmentId, String idempotencyKey, List<LocationPointRequest> points) {
        return trackingClient.uploadRoute(shipmentId, keyOrRandom(idempotencyKey), points);
    }

    public LiveLocationStreamResponse streamLiveLocations(long shipmentId, List<LocationPointRequest> points) {
        return LiveLocationStreamResponse.of(
                shipmentId,
                points.size(),
                trackingClient.streamLiveLocations(shipmentId, points)
        );
    }

    private static String keyOrRandom(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey.trim();
    }
}
