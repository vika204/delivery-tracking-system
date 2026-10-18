package com.delivery.shipment.metrics;

import com.delivery.shipment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipmentMetricsTest {

    private MeterRegistry registry;
    private OutboxEventRepository outboxRepository;
    private ShipmentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        outboxRepository = mock(OutboxEventRepository.class);
        metrics = new ShipmentMetrics(registry, outboxRepository);
    }

    @Test
    void createdCounterAndWeightSummaryAreRecorded() {
        metrics.shipmentCreated(new BigDecimal("2.5"));
        metrics.shipmentCreated(new BigDecimal("7.5"));

        assertThat(registry.get("delivery.shipments.registered").counter().count()).isEqualTo(2);
        var weight = registry.get("delivery.shipments.weight").summary();
        assertThat(weight.count()).isEqualTo(2);
        assertThat(weight.totalAmount()).isEqualTo(10.0);
        assertThat(weight.getId().getBaseUnit()).isEqualTo("kilograms");
    }

    @Test
    void failedCounterIsIncremented() {
        metrics.shipmentFailed();

        assertThat(registry.get("delivery.shipments.failed").counter().count()).isEqualTo(1);
    }

    @Test
    void pendingGaugeReflectsRepositoryOnEachRead() {
        when(outboxRepository.countByPublishedFalse()).thenReturn(3L, 7L);

        var gauge = registry.get("delivery.outbox.pending").gauge();

        assertThat(gauge.value()).isEqualTo(3.0);
        assertThat(gauge.value()).isEqualTo(7.0);
    }
}
