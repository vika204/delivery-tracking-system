package com.delivery.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipments", schema = "shipment")
@Getter
@Setter
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

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}