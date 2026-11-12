package com.delivery.webui.service;

import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.DispatchCourierResponse;
import com.delivery.webui.client.dto.ShipmentDetailsResponse;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.viewmodel.TrackingPageViewModel;
import com.delivery.webui.viewmodel.TrackingTimelineStepViewModel;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TrackingViewService {

    static final int LOCATION_PAGE_SIZE = 100;
    static final int MAX_LOCATION_COUNT = 500;
    private static final String NOT_AVAILABLE = "Not available";
    private static final String NOT_ASSIGNED = "Not assigned";

    private final TrackingShipmentClient shipmentClient;
    private final TrackingLocationClient locationClient;
    private final DispatchCourierClient courierClient;

    public TrackingViewService(
            TrackingShipmentClient shipmentClient,
            TrackingLocationClient locationClient,
            DispatchCourierClient courierClient
    ) {
        this.shipmentClient = shipmentClient;
        this.locationClient = locationClient;
        this.courierClient = courierClient;
    }

    public TrackingPageViewModel getEmptyTracking() {
        return new TrackingPageViewModel(
                false,
                "—",
                "Enter a shipment ID to start tracking",
                NOT_AVAILABLE,
                NOT_AVAILABLE,
                List.of(),
                List.of(),
                true,
                true,
                "WAITING FOR COURIER LOCATION",
                "Enter a shipment ID to start tracking",
                false
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
        String destinationText = loadDestination(shipmentId);
        String courierText = loadCourier(shipmentId);
        LocationHistory locationHistory = loadLocationHistory(shipmentId);
        LocationPresentation locationPresentation =
                presentLocationHistory(locationHistory);

        return new TrackingPageViewModel(
                true,
                shipment.shipmentId().toString(),
                statusText,
                destinationText,
                courierText,
                List.of(
                        new TrackingTimelineStepViewModel(
                                locationPresentation.title(),
                                locationPresentation.description(),
                                false,
                                true
                        )
                ),
                locationHistory.locations(),
                locationHistory.available(),
                locationHistory.complete(),
                locationPresentation.mapMessage(),
                locationPresentation.description(),
                isTerminalStatus(shipment.status())
        );
    }

    private String loadDestination(long shipmentId) {
        try {
            ShipmentDetailsResponse details =
                    shipmentClient.getShipmentDetails(shipmentId);

            if (details == null
                    || details.shipmentId() == null
                    || details.shipmentId() != shipmentId
                    || details.deliveryAddress() == null
                    || details.deliveryAddress().isBlank()) {
                return NOT_AVAILABLE;
            }

            return details.deliveryAddress().trim();
        } catch (RestClientException ex) {
            return NOT_AVAILABLE;
        }
    }

    private String loadCourier(long shipmentId) {
        try {
            DispatchCourierResponse response =
                    courierClient.getShipmentCourier(shipmentId);

            if (response == null
                    || response.shipmentId() == null
                    || response.shipmentId() != shipmentId) {
                return NOT_AVAILABLE;
            }

            if (!response.assigned()) {
                return NOT_ASSIGNED;
            }

            if (response.courierName() == null
                    || response.courierName().isBlank()) {
                return NOT_AVAILABLE;
            }

            return response.courierName().trim();
        } catch (RestClientException ex) {
            return NOT_AVAILABLE;
        }
    }

    private LocationHistory loadLocationHistory(long shipmentId) {
        try {
            List<TrackingLocationResponse> validLocations = new ArrayList<>();
            long afterLocationId = 0;
            int loadedLocationCount = 0;

            while (loadedLocationCount < MAX_LOCATION_COUNT) {
                int pageLimit = Math.min(
                        LOCATION_PAGE_SIZE,
                        MAX_LOCATION_COUNT - loadedLocationCount
                );
                List<TrackingLocationResponse> page = locationClient
                        .getShipmentLocations(
                                shipmentId,
                                afterLocationId,
                                pageLimit
                        );

                if (page.isEmpty()) {
                    return LocationHistory.complete(validLocations);
                }

                afterLocationId = validatePage(
                        shipmentId,
                        afterLocationId,
                        pageLimit,
                        page
                );
                loadedLocationCount += page.size();

                page.stream()
                        .filter(this::hasValidCoordinates)
                        .forEach(validLocations::add);

                if (page.size() < pageLimit) {
                    return LocationHistory.complete(validLocations);
                }
            }

            List<TrackingLocationResponse> nextPage = locationClient
                    .getShipmentLocations(shipmentId, afterLocationId, 1);

            if (!nextPage.isEmpty()) {
                validatePage(shipmentId, afterLocationId, 1, nextPage);
                return LocationHistory.incomplete(validLocations);
            }

            return LocationHistory.complete(validLocations);
        } catch (RestClientException | InvalidLocationHistoryException ex) {
            return LocationHistory.unavailable();
        }
    }

    private long validatePage(
            long shipmentId,
            long afterLocationId,
            int requestedLimit,
            List<TrackingLocationResponse> page
    ) {
        if (page.size() > requestedLimit) {
            throw new InvalidLocationHistoryException();
        }

        long previousLocationId = afterLocationId;

        for (TrackingLocationResponse location : page) {
            if (location == null
                    || location.locationId() == null
                    || location.shipmentId() == null
                    || location.shipmentId() != shipmentId
                    || location.locationId() <= previousLocationId) {
                throw new InvalidLocationHistoryException();
            }

            previousLocationId = location.locationId();
        }

        return previousLocationId;
    }

    private boolean hasValidCoordinates(TrackingLocationResponse location) {
        return isWithin(location.latitude(), "-90", "90")
                && isWithin(location.longitude(), "-180", "180");
    }

    private boolean isWithin(BigDecimal value, String minimum, String maximum) {
        return value != null
                && value.compareTo(new BigDecimal(minimum)) >= 0
                && value.compareTo(new BigDecimal(maximum)) <= 0;
    }

    private LocationPresentation presentLocationHistory(
            LocationHistory locationHistory
    ) {
        if (!locationHistory.available()) {
            return new LocationPresentation(
                    "Location data temporarily unavailable",
                    "Shipment status is still available.",
                    "LOCATION DATA TEMPORARILY UNAVAILABLE"
            );
        }

        if (!locationHistory.complete()) {
            return new LocationPresentation(
                    "Location history is incomplete",
                    "Showing the first 500 GPS points. The latest courier position is not available.",
                    "LOCATION HISTORY INCOMPLETE"
            );
        }

        if (locationHistory.locations().isEmpty()) {
            return new LocationPresentation(
                    "Waiting for courier location",
                    "No GPS location has been recorded yet.",
                    "WAITING FOR COURIER LOCATION"
            );
        }

        return new LocationPresentation(
                "Courier location received",
                locationHistory.locations().size() == 1
                        ? "1 GPS point available."
                        : locationHistory.locations().size() + " GPS points available.",
                "COURIER LOCATION AVAILABLE"
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

    private boolean isTerminalStatus(String status) {
        return "DELIVERED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private record LocationHistory(
            List<TrackingLocationResponse> locations,
            boolean available,
            boolean complete
    ) {

        private LocationHistory {
            locations = List.copyOf(locations);
        }

        static LocationHistory complete(List<TrackingLocationResponse> locations) {
            return new LocationHistory(locations, true, true);
        }

        static LocationHistory incomplete(List<TrackingLocationResponse> locations) {
            return new LocationHistory(locations, true, false);
        }

        static LocationHistory unavailable() {
            return new LocationHistory(List.of(), false, false);
        }
    }

    private record LocationPresentation(
            String title,
            String description,
            String mapMessage
    ) {
    }

    private static final class InvalidLocationHistoryException
            extends RuntimeException {
    }
}
