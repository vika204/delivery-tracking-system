package com.delivery.dispatch.metrics;

import com.delivery.dispatch.entity.DispatchRequestStatus;
import com.delivery.dispatch.repository.DispatchRequestRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DispatchMetrics {

    private final MeterRegistry registry;
    private final Counter withdrawals;

    public DispatchMetrics(MeterRegistry registry, DispatchRequestRepository requestRepository) {
        this.registry = registry;
        this.withdrawals = Counter.builder("delivery.dispatch.assignments.withdrawn")
                .description("Assignments the courier withdrew from before pickup")
                .register(registry);
        Gauge.builder("delivery.dispatch.requests.waiting", requestRepository,
                        repository -> repository.countByStatus(DispatchRequestStatus.WAITING_FOR_COURIERS))
                .description("Shipments waiting because no suitable courier is available")
                .register(registry);
    }

    /** outcome: sent, accepted, declined, expired, cancelled */
    public void offer(String outcome) {
        Counter.builder("delivery.dispatch.offers")
                .description("Courier offers by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void assignmentWithdrawn() {
        withdrawals.increment();
    }
}
