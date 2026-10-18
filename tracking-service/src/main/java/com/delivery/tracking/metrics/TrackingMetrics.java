package com.delivery.tracking.metrics;

import com.delivery.tracking.repository.TrackingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class TrackingMetrics {

    private final MeterRegistry registry;
    private final Counter fallbacks;
    private final DistributionSummary batchSize;

    public TrackingMetrics(MeterRegistry registry, TrackingRepository trackingRepository) {
        this.registry = registry;
        this.fallbacks = Counter.builder("delivery.tracking.fallbacks")
                .description("Requests answered with the fallback because shipment-service was unavailable")
                .register(registry);
        this.batchSize = DistributionSummary.builder("delivery.tracking.batch.size")
                .description("Number of shipment ids per batch lookup")
                .baseUnit("ids")
                .serviceLevelObjectives(1, 5, 10, 25, 50, 100)
                .register(registry);
        Gauge.builder("delivery.tracking.locations.stored", trackingRepository, TrackingRepository::count)
                .description("Tracking locations stored in the database")
                .register(registry);
    }

    public void fallbackUsed() {
        fallbacks.increment();
    }

    public void batchRequested(int ids) {
        batchSize.record(ids);
    }

    public <T> T timeShipmentCall(String operation, Supplier<T> call) {
        Timer timer = Timer.builder("delivery.tracking.shipment.client")
                .description("Latency of calls from tracking-service to shipment-service, per attempt")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .register(registry);
        return timer.record(call);
    }
}
