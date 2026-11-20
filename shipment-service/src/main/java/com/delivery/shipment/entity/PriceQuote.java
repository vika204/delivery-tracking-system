package com.delivery.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceQuote {

    @Id
    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "pickup_city_code", nullable = false, length = 19)
    private String pickupCityCode;

    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_city_code", nullable = false, length = 19)
    private String deliveryCityCode;

    @Column(name = "delivery_latitude", nullable = false)
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude", nullable = false)
    private Double deliveryLongitude;

    @Column(name = "distance_km", nullable = false)
    private BigDecimal distanceKm;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private DeliveryType deliveryType;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "price_per_km", nullable = false)
    private BigDecimal pricePerKm;

    @Column(name = "price_per_kg", nullable = false)
    private BigDecimal pricePerKg;

    @Column(name = "distance_charge", nullable = false)
    private BigDecimal distanceCharge;

    @Column(name = "weight_charge", nullable = false)
    private BigDecimal weightCharge;

    @Column(name = "delivery_type_multiplier", nullable = false)
    private BigDecimal deliveryTypeMultiplier;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "shipment_id")
    private Long shipmentId;
}
