package com.delivery.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter deadLettered;
    private final Timer processing;

    public NotificationMetrics(MeterRegistry registry) {
        this.deadLettered = Counter.builder("delivery.notifications.dlt")
                .description("Shipment events that exhausted retries or were rejected and moved to the DLT")
                .register(registry);
        this.processing = Timer.builder("delivery.notifications.event.processing")
                .description("Time to process one shipment event, including failed attempts")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void deadLettered() {
        deadLettered.increment();
    }

    public void timeProcessing(Runnable processing) {
        this.processing.record(processing);
    }
}
