package com.delivery.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Запис таймлайну доставки: кожна зміна статусу (US-1.5). */
@Entity
@Table(name = "shipment_status_history", indexes = @Index(name = "idx_status_history_shipment", columnList = "shipment_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    // null для першого запису (створення доставки)
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ShipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ShipmentStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason", length = 500)
    private String reason;
}
