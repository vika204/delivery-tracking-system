package com.delivery.tracking.metrics;

import com.delivery.tracking.repository.TrackingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingMetricsTest {

    private MeterRegistry registry;
    private TrackingRepository repository;
    private TrackingMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        repository = mock(TrackingRepository.class);
        metrics = new TrackingMetrics(registry, repository);
    }

    @Test
    void fallbackCounterIsIncremented() {
        metrics.fallbackUsed();
        metrics.fallbackUsed();

        assertThat(registry.get("delivery.tracking.fallbacks").counter().count()).isEqualTo(2);
    }

    @Test
    void batchSizeIsRecordedInSummary() {
        metrics.batchRequested(3);
        metrics.batchRequested(7);

        var summary = registry.get("delivery.tracking.batch.size").summary();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(10.0);
    }

    @Test
    void shipmentCallTimerIsTaggedByOperationAndReturnsTheResult() {
        String result = metrics.timeShipmentCall("get", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(registry.get("delivery.tracking.shipment.client").tag("operation", "get").timer().count())
                .isEqualTo(1);
    }

    @Test
    void shipmentCallTimerAlsoRecordsFailuresAndRethrows() {
        assertThatThrownBy(() -> metrics.timeShipmentCall("create", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("delivery.tracking.shipment.client").tag("operation", "create").timer().count())
                .isEqualTo(1);
    }

    @Test
    void storedLocationsGaugeReadsRepositoryOnEachRead() {
        when(repository.count()).thenReturn(2L, 5L);

        var gauge = registry.get("delivery.tracking.locations.stored").gauge();

        assertThat(gauge.value()).isEqualTo(2.0);
        assertThat(gauge.value()).isEqualTo(5.0);
    }
}
