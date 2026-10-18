package com.delivery.dispatch.metrics;

import com.delivery.dispatch.repository.CourierRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CourierMetrics {

    private final Counter registered;
    private final DistributionSummary capacity;

    public CourierMetrics(MeterRegistry registry, CourierRepository courierRepository) {
        this.registered = Counter.builder("delivery.couriers.registered")
                .description("Couriers registered through the API")
                .register(registry);
        this.capacity = DistributionSummary.builder("delivery.couriers.capacity")
                .description("Maximum package weight of registered couriers")
                .baseUnit("kilograms")
                .serviceLevelObjectives(5, 10, 25, 50, 100)
                .register(registry);
        Gauge.builder("delivery.couriers.available", courierRepository, CourierRepository::countByIsAvailableTrue)
                .description("Couriers currently marked as available")
                .register(registry);
    }

    public void courierRegistered(BigDecimal maxPackageWeight) {
        registered.increment();
        if (maxPackageWeight != null) {
            capacity.record(maxPackageWeight.doubleValue());
        }
    }
}
