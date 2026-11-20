package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "couriers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Courier {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "work_zone", nullable = false)
    private String workZone;

    @Column(name = "work_zone_code", length = 19)
    private String workZoneCode;

    @Column(name = "max_package_weight", nullable = false)
    private BigDecimal maxPackageWeight;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Column(name = "last_known_latitude")
    private Double lastKnownLatitude;

    @Column(name = "last_known_longitude")
    private Double lastKnownLongitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;
}
