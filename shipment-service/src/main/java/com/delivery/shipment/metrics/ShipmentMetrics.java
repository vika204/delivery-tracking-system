package com.delivery.shipment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.delivery.shipment.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ShipmentMetrics {

    private final Counter created;
    private final Counter failed;
    private final DistributionSummary weight;

    public ShipmentMetrics(MeterRegistry registry, OutboxEventRepository outboxRepository) {
        this.created = Counter.builder("delivery.shipments.registered")
                .description("Shipments successfully created (idempotent replays are not counted)")
                .register(registry);
        this.failed = Counter.builder("delivery.shipments.failed")
                .description("Shipment creations that failed with an unexpected error")
                .register(registry);
        this.weight = DistributionSummary.builder("delivery.shipments.weight")
                .description("Weight of created shipments")
                .baseUnit("kilograms")
                .serviceLevelObjectives(1, 5, 10, 25, 50, 100)
                .register(registry);
        Gauge.builder("delivery.outbox.pending", outboxRepository, OutboxEventRepository::countByPublishedFalse)
                .description("Outbox events not yet published to Kafka")
                .register(registry);
    }

    public void shipmentCreated(BigDecimal weightKg) {
        created.increment();
        weight.record(weightKg.doubleValue());
    }

    public void shipmentFailed() {
        failed.increment();
    }
}
