package com.delivery.webui.service;

import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.dto.CourierShipmentResponse;
import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import org.springframework.stereotype.Service;

/**
 * Доставка очима кур'єра: забір посилки й запис GPS. Перед записом точки shipment-service
 * підтверджує, що посилку призначено цьому кур'єру (інакше 403) і вона вже в дорозі.
 */
@Service
public class CourierDeliveryService {

    private final CourierShipmentClient shipmentClient;
    private final TrackingLocationClient trackingClient;

    public CourierDeliveryService(CourierShipmentClient shipmentClient, TrackingLocationClient trackingClient) {
        this.shipmentClient = shipmentClient;
        this.trackingClient = trackingClient;
    }

    public CourierShipmentResponse view(long courierId, long shipmentId) {
        return shipmentClient.courierView(courierId, shipmentId);
    }

    public CourierShipmentResponse confirmPickup(long courierId, long shipmentId) {
        shipmentClient.confirmPickup(courierId, shipmentId);
        return view(courierId, shipmentId);
    }

    public CourierShipmentResponse completeDelivery(long courierId, long shipmentId) {
        shipmentClient.completeDelivery(courierId, shipmentId);
        return view(courierId, shipmentId);
    }

    public TrackingLocationResponse recordLocation(long courierId, long shipmentId, RecordLocationRequest point) {
        CourierShipmentResponse shipment = view(courierId, shipmentId);
        if (!shipment.inTransit()) {
            throw new ShipmentNotInTransitException(shipmentId, shipment.status());
        }
        return trackingClient.recordLocation(shipmentId, point);
    }

    public static class ShipmentNotInTransitException extends RuntimeException {
        public ShipmentNotInTransitException(long shipmentId, String status) {
            super("GPS can be recorded only while shipment " + shipmentId + " is IN_TRANSIT, current status is " + status);
        }
    }
}
