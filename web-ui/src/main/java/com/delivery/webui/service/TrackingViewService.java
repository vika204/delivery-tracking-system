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

    public TrackingPageViewModel getDemoTracking() {
        return new TrackingPageViewModel(
                "WE-204-081",
                "8–12",
                "YOUR COURIER IS ON THE WAY",
                "AL",
                "Alex · Demo courier",
                "Express delivery",
                "Sumy, Ukraine",
                "Test destination B",
                List.of(
                        new TrackingTimelineStepViewModel(
                                "Package collected",
                                "14:02 · Pickup point",
                                true,
                                false
                        ),
                        new TrackingTimelineStepViewModel(
                                "Out for delivery",
                                "Simulated GPS · Sumy, Ukraine",
                                false,
                                true
                        )
                )
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
                shipment.shipmentId().toString(),
                "—",
                statusText,
                "—",
                "Not available yet",
                "Courier information is not connected",
                "Not available yet",
                "",
                List.of(
                        new TrackingTimelineStepViewModel(
                                statusText,
                                "Current status from shipment-service",
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