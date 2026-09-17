package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @Column(name = "work_zone", nullable = false)
    private String workZone;

    @Column(name = "max_package_weight", nullable = false)
    private BigDecimal maxPackageWeight;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;
}