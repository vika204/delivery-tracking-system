package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/** Пропозиція доставки кур'єру (CourierProposal). Одночасно в кур'єра — не більше однієї PENDING. */
@Entity
@Table(name = "delivery_offers", indexes = {
        @Index(name = "idx_offer_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_offer_shipment", columnList = "shipment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Courier courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OfferStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    // Орієнтовна відстань кур'єра до точки забору на момент пропозиції.
    @Column(name = "distance_to_pickup_km")
    private Double distanceToPickupKm;
}
