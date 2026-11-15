package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/** Призначення кур'єра на доставку (CourierAssignment). */
@Entity
@Table(name = "assignments", indexes = @Index(name = "idx_assignment_shipment", columnList = "shipment_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Courier courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssignmentStatus status;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "end_reason", length = 500)
    private String endReason;
}
