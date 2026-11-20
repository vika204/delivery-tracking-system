package com.delivery.shipment.entity;

import com.delivery.shipment.exception.ShipmentAccessDeniedException;
import com.delivery.shipment.exception.ShipmentStatusConflictException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_phone")
    private String senderPhone;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "pickup_city_code", length = 19)
    private String pickupCityCode;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_city_code", length = 19)
    private String deliveryCityCode;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Column(name = "length", nullable = false)
    private BigDecimal length;

    @Column(name = "width", nullable = false)
    private BigDecimal width;

    @Column(name = "height", nullable = false)
    private BigDecimal height;

    @Column(name = "distance", nullable = false)
    private BigDecimal distance;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @Column(name = "assigned_courier_id")
    private Long assignedCourierId;

    @Column(name = "assigned_courier_name", length = 100)
    private String assignedCourierName;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "trip_started_at")
    private Instant tripStartedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "timestamp(6) default now()")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", columnDefinition = "bigint default 0")
    private Long version;

    public void cancelBySender(long senderId, String reason, Instant at) {
        requireSender(senderId);
        if (status == ShipmentStatus.CANCELLED) {
            return;
        }
        if (!ShipmentStatus.BEFORE_PICKUP.contains(status)) {
            throw new ShipmentStatusConflictException(status == ShipmentStatus.DELIVERED
                    ? "Shipment " + shipmentId + " is already delivered"
                    : "Shipment " + shipmentId + " cannot be cancelled: the courier has already picked up the parcel");
        }
        status = ShipmentStatus.CANCELLED;
        cancelledAt = at;
        cancellationReason = reason;
    }

    public void requireEditableBy(long senderId) {
        requireSender(senderId);
        if (status != ShipmentStatus.WAITING_FOR_COURIER) {
            throw new ShipmentStatusConflictException("Shipment " + shipmentId
                    + " can be edited only before a courier is assigned; cancel it and create a new one instead");
        }
    }

    public void updateContacts(String senderName, String senderPhone, String recipientName, String recipientPhone) {
        if (senderName != null) this.senderName = senderName;
        if (senderPhone != null) this.senderPhone = senderPhone;
        if (recipientName != null) this.recipientName = recipientName;
        if (recipientPhone != null) this.recipientPhone = recipientPhone;
    }

    public void updateDimensions(BigDecimal length, BigDecimal width, BigDecimal height) {
        if (length != null) this.length = length;
        if (width != null) this.width = width;
        if (height != null) this.height = height;
    }

    public void updateRoute(
            String pickupAddress, String pickupCityCode, double pickupLatitude, double pickupLongitude,
            String deliveryAddress, String deliveryCityCode, double deliveryLatitude, double deliveryLongitude,
            BigDecimal weight, BigDecimal distance, BigDecimal price
    ) {
        this.pickupAddress = pickupAddress;
        this.pickupCityCode = pickupCityCode;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.deliveryAddress = deliveryAddress;
        this.deliveryCityCode = deliveryCityCode;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.weight = weight;
        this.distance = distance;
        this.price = price;
    }

    public boolean assignCourier(long courierId, String courierName, Instant at) {
        if (status != ShipmentStatus.WAITING_FOR_COURIER) {
            return false;
        }
        status = ShipmentStatus.ASSIGNED;
        assignedCourierId = courierId;
        assignedCourierName = courierName;
        assignedAt = at;
        tripStartedAt = null;
        return true;
    }

    public boolean unassignCourier(long courierId) {
        if (!Objects.equals(assignedCourierId, courierId)
                || (status != ShipmentStatus.ASSIGNED && status != ShipmentStatus.TO_PICKUP)) {
            return false;
        }
        status = ShipmentStatus.WAITING_FOR_COURIER;
        assignedCourierId = null;
        assignedCourierName = null;
        assignedAt = null;
        tripStartedAt = null;
        return true;
    }

    public boolean startTripToPickup(long courierId, Instant at) {
        requireAssignedCourier(courierId);
        if (status == ShipmentStatus.TO_PICKUP) {
            return false;
        }
        requireStatus(ShipmentStatus.ASSIGNED, "start the trip to pickup");
        status = ShipmentStatus.TO_PICKUP;
        tripStartedAt = at;
        return true;
    }

    public boolean confirmPickup(long courierId, Instant at) {
        requireAssignedCourier(courierId);
        if (status == ShipmentStatus.IN_TRANSIT) {
            return false;
        }
        if (status != ShipmentStatus.ASSIGNED && status != ShipmentStatus.TO_PICKUP) {
            throw conflict("confirm pickup");
        }
        if (tripStartedAt == null) {
            tripStartedAt = at;
        }
        status = ShipmentStatus.IN_TRANSIT;
        pickedUpAt = at;
        return true;
    }

    public boolean completeDelivery(long courierId, Instant at) {
        requireAssignedCourier(courierId);
        if (status == ShipmentStatus.DELIVERED) {
            return false;
        }
        requireStatus(ShipmentStatus.IN_TRANSIT, "complete the delivery");
        status = ShipmentStatus.DELIVERED;
        deliveredAt = at;
        return true;
    }

    public boolean isSender(long userId) {
        return Objects.equals(this.userId, userId);
    }

    public boolean isAssignedCourier(long courierId) {
        return Objects.equals(assignedCourierId, courierId);
    }

    private void requireSender(long senderId) {
        if (!isSender(senderId)) {
            throw new ShipmentAccessDeniedException("Only the sender can manage shipment " + shipmentId);
        }
    }

    private void requireAssignedCourier(long courierId) {
        if (!isAssignedCourier(courierId)) {
            throw new ShipmentAccessDeniedException("Courier " + courierId + " is not assigned to shipment " + shipmentId);
        }
    }

    private void requireStatus(ShipmentStatus expected, String action) {
        if (status != expected) {
            throw conflict(action);
        }
    }

    private ShipmentStatusConflictException conflict(String action) {
        return new ShipmentStatusConflictException(
                "Cannot " + action + " for shipment " + shipmentId + " in status " + status);
    }
}
