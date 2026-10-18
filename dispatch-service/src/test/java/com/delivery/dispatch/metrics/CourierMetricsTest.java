package com.delivery.dispatch.metrics;

import com.delivery.dispatch.repository.CourierRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourierMetricsTest {

    private MeterRegistry registry;
    private CourierRepository repository;
    private CourierMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        repository = mock(CourierRepository.class);
        metrics = new CourierMetrics(registry, repository);
    }

    @Test
    void registrationIncrementsCounterAndRecordsCapacity() {
        metrics.courierRegistered(new BigDecimal("12.5"));
        metrics.courierRegistered(new BigDecimal("30"));

        assertThat(registry.get("delivery.couriers.registered").counter().count()).isEqualTo(2);
        var capacity = registry.get("delivery.couriers.capacity").summary();
        assertThat(capacity.count()).isEqualTo(2);
        assertThat(capacity.totalAmount()).isEqualTo(42.5);
        assertThat(capacity.getId().getBaseUnit()).isEqualTo("kilograms");
    }

    @Test
    void availableGaugeReadsRepositoryOnEachRead() {
        when(repository.countByIsAvailableTrue()).thenReturn(4L, 1L);

        var gauge = registry.get("delivery.couriers.available").gauge();

        assertThat(gauge.value()).isEqualTo(4.0);
        assertThat(gauge.value()).isEqualTo(1.0);
    }
}
