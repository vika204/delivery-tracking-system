package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Кур'єр у Dispatch Context. {@code isAvailable} — перемикач «на лінії» (US-3.5):
 * лише кур'єри на лінії отримують нові пропозиції; активні доставки можна завершити й офлайн.
 */
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

    // Назва зони для людей (наприклад, "Kyiv").
    @Column(name = "work_zone", nullable = false)
    private String workZone;

    // KATOTTG-код населеного пункту зони; за ним підбираються доставки поблизу.
    @Column(name = "work_zone_code", length = 19)
    private String workZoneCode;

    @Column(name = "max_package_weight", nullable = false)
    private BigDecimal maxPackageWeight;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    // Snapshot останньої позиції (у майбутньому — з події Tracking CourierLocationUpdated).
    @Column(name = "last_known_latitude")
    private Double lastKnownLatitude;

    @Column(name = "last_known_longitude")
    private Double lastKnownLongitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    // Для чесної черги між рівноцінними кандидатами.
    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;
}
