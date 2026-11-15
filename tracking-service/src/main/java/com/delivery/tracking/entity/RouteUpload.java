package com.delivery.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Журнал завершених Client Streaming-вивантажень маршруту.
 * Первинний ключ upload_id робить повторну спробу того самого вивантаження ідемпотентною.
 */
@Entity
@Table(name = "route_uploads")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RouteUpload {

    @Id
    @Column(name = "upload_id", length = 64)
    private String uploadId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "received_count", nullable = false)
    private Integer receivedCount;

    @Column(name = "saved_count", nullable = false)
    private Integer savedCount;

    @Column(name = "rejected_count", nullable = false)
    private Integer rejectedCount;

    // null, якщо жодну координату не збережено
    @Column(name = "first_location_id")
    private Long firstLocationId;

    @Column(name = "last_location_id")
    private Long lastLocationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
