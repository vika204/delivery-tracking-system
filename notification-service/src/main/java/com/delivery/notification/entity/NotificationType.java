package com.delivery.notification.entity;

public enum NotificationType {
    SHIPMENT_CREATED("Your shipment has been created."),
    COURIER_ASSIGNED("A courier has been assigned to your shipment."),
    COURIER_REASSIGNMENT("We are looking for another courier for your shipment."),
    PACKAGE_PICKED_UP("Your package has been picked up and is on its way."),
    DELIVERED("Your package has been delivered."),
    SHIPMENT_CANCELLED("Your shipment has been cancelled.");

    private final String message;

    NotificationType(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public static NotificationType fromShipmentStatus(String status) {
        return switch (status) {
            case "ASSIGNED" -> COURIER_ASSIGNED;
            case "WAITING_FOR_COURIER" -> COURIER_REASSIGNMENT;
            case "IN_TRANSIT" -> PACKAGE_PICKED_UP;
            case "DELIVERED" -> DELIVERED;
            case "CANCELLED" -> SHIPMENT_CANCELLED;
            default -> null;
        };
    }
}
