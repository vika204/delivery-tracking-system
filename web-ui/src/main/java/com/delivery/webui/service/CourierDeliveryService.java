package com.delivery.webui.service;

import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.dto.CourierShipmentResponse;
import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CourierDeliveryService {

    private final CourierShipmentClient shipmentClient;
    private final TrackingLocationClient trackingClient;
    private final DispatchCourierDashboardClient dispatchClient;

    public CourierDeliveryService(CourierShipmentClient shipmentClient, TrackingLocationClient trackingClient,
                                  DispatchCourierDashboardClient dispatchClient) {
        this.shipmentClient = shipmentClient;
        this.trackingClient = trackingClient;
        this.dispatchClient = dispatchClient;
    }

    public CourierShipmentResponse view(long courierId, long shipmentId) {
        return shipmentClient.courierView(courierId, shipmentId);
    }

    public CourierShipmentResponse confirmPickup(long courierId, long shipmentId) {
        return shipmentClient.confirmPickup(courierId, shipmentId);
    }

    public CourierShipmentResponse completeDelivery(long courierId, long shipmentId) {
        return shipmentClient.completeDelivery(courierId, shipmentId);
    }

    public void withdraw(long courierId, long shipmentId) {
        dispatchClient.withdrawShipment(courierId, shipmentId);
    }

    public ResponseEntity<TrackingLocationResponse> recordLocation(long courierId, long shipmentId, RecordLocationRequest point) {
        return trackingClient.recordLocation(shipmentId, point);
    }

}
