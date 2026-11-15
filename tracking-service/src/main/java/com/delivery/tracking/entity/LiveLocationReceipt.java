package com.delivery.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "live_location_receipts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LiveLocationReceipt {

    @Id
    @Column(name = "point_id", length = 64)
    private String pointId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "distance_meters", nullable = false)
    private Double distanceMeters;

    @Column(name = "speed_kmh", nullable = false)
    private Double speedKmh;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
