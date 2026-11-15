package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Snapshot доставки з Shipment (ShipmentCreated / ShipmentDetailsUpdated) і стан пошуку кур'єра.
 * Dispatch повторює пошук без звернень до Shipment.
 * Поля snapshot nullable: «надгробок» скасованої доставки може з'явитися раніше за ShipmentCreated.
 */
@Entity
@Table(name = "dispatch_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchRequest {

    @Id
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "pickup_city_code", length = 19)
    private String pickupCityCode;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_city_code", length = 19)
    private String deliveryCityCode;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(name = "parcel_weight")
    private BigDecimal parcelWeight;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "delivery_type", length = 20)
    private String deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DispatchRequestStatus status;

    @Column(name = "search_attempts", nullable = false)
    private int searchAttempts;

    @Column(name = "last_search_at")
    private Instant lastSearchAt;

    // Коли відправника повідомили про затримку пошуку; null — ще не повідомляли.
    @Column(name = "delay_notified_at")
    private Instant delayNotifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isExpress() {
        return "EXPRESS".equals(deliveryType);
    }

    public boolean isSearchable() {
        return status == DispatchRequestStatus.SEARCHING || status == DispatchRequestStatus.WAITING_FOR_COURIERS;
    }

    public boolean hasRoute() {
        return pickupCityCode != null && pickupLatitude != null && pickupLongitude != null && parcelWeight != null;
    }
}
