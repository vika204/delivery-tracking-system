package com.delivery.webui.viewmodel;

import com.delivery.webui.client.dto.ClientShipmentResponse;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record ParcelViewModel(
        long shipmentId,
        String status,
        String pickupAddress,
        String deliveryAddress,
        String recipientName,
        String recipientPhone,
        String createdAt,
        String distance,
        String weight,
        String length,
        String width,
        String height,
        String price,
        String courierName,
        String courierPhone
) {

    private static final String NOT_AVAILABLE = "Not available";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);

    public static ParcelViewModel from(ClientShipmentResponse shipment) {
        String courierName = shipment.assignedCourierId() == null
                ? "Not assigned" : display(shipment.assignedCourierName());
        BigDecimal distance = shipment.distance();
        String distanceText = distance != null && distance.signum() > 0 && distance.compareTo(BigDecimal.ONE) < 0
                ? quantity(distance.movePointRight(3), "m") : quantity(distance, "km");

        return new ParcelViewModel(
                shipment.shipmentId(),
                shipment.status() == null ? "NOT_AVAILABLE" : shipment.status().trim().toUpperCase(Locale.ROOT),
                display(shipment.pickupAddress()),
                display(shipment.deliveryAddress()),
                display(shipment.recipientName()),
                display(shipment.recipientPhone()),
                shipment.createdAt() == null ? NOT_AVAILABLE : DATE_FORMAT.format(shipment.createdAt()),
                distanceText,
                quantity(shipment.weight(), "kg"),
                quantity(shipment.length(), "cm"),
                quantity(shipment.width(), "cm"),
                quantity(shipment.height(), "cm"),
                quantity(shipment.price(), "UAH"),
                courierName,
                NOT_AVAILABLE
        );
    }

    public String statusLabel() {
        return status.replace('_', ' ');
    }

    public String statusClass() {
        return switch (status) {
            case "IN_TRANSIT", "ASSIGNED", "TO_PICKUP" -> "transit";
            case "DELIVERED" -> "delivered";
            case "WAITING_FOR_COURIER" -> "waiting";
            case "CANCELLED" -> "cancelled";
            default -> "";
        };
    }

    public boolean delivered() {
        return "DELIVERED".equals(status);
    }

    private static String quantity(BigDecimal value, String unit) {
        return value == null ? NOT_AVAILABLE : value.stripTrailingZeros().toPlainString() + " " + unit;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value.trim();
    }
}
