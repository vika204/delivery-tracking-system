package com.delivery.notification.service;

import com.delivery.notification.dto.DeliveryInfoResponse;
import com.delivery.notification.grpc.TrackingGrpcClient;
import org.springframework.stereotype.Service;

@Service
public class DeliveryTrackingService {

    private final TrackingGrpcClient trackingClient;

    public DeliveryTrackingService(TrackingGrpcClient trackingClient) {
        this.trackingClient = trackingClient;
    }

    public DeliveryInfoResponse getDeliveryInfo(long shipmentId) {
        return trackingClient.getDeliveryInfo(shipmentId);
    }

    public boolean subscribe(long shipmentId) {
        return trackingClient.subscribe(shipmentId);
    }

    public boolean unsubscribe(long shipmentId) {
        return trackingClient.unsubscribe(shipmentId);
    }
}
