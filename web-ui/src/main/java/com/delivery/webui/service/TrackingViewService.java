package com.delivery.webui.service;

import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.viewmodel.TrackingPageViewModel;
import com.delivery.webui.viewmodel.TrackingTimelineStepViewModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class TrackingViewService {

    private final TrackingShipmentClient shipmentClient;

    public TrackingViewService(TrackingShipmentClient shipmentClient) {
        this.shipmentClient = shipmentClient;
    }

    public TrackingPageViewModel getEmptyTracking() {
        return new TrackingPageViewModel(
                false,
                "—",
                "Enter a shipment ID to start tracking",
                List.of()
        );
    }

    public TrackingPageViewModel getLiveTracking(long shipmentId) {
        TrackingShipmentResponse shipment =
                shipmentClient.getShipmentById(shipmentId);

        if (shipment == null
                || shipment.shipmentId() == null
                || !shipment.shipmentId().equals(shipmentId)
                || shipment.status() == null
                || shipment.status().isBlank()
                || "UNAVAILABLE".equalsIgnoreCase(shipment.status())) {
            throw new IllegalStateException(
                    "Shipment data is temporarily unavailable"
            );
        }

        String statusText = formatStatus(shipment.status());

        return new TrackingPageViewModel(
                true,
                shipment.shipmentId().toString(),
                statusText,
                List.of(
                        new TrackingTimelineStepViewModel(
                                "Waiting for courier location",
                                "Real-time GPS is not connected yet",
                                false,
                                true
                        )
                )
        );
    }

    private String formatStatus(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "WAITING_FOR_COURIER" -> "WAITING FOR COURIER";
            case "ASSIGNED" -> "COURIER ASSIGNED";
            case "TO_PICKUP" -> "COURIER GOING TO PICKUP";
            case "IN_TRANSIT" -> "YOUR COURIER IS ON THE WAY";
            case "DELIVERED" -> "DELIVERED";
            case "CANCELLED" -> "CANCELLED";
            default -> status.replace('_', ' ');
        };
    }
}
