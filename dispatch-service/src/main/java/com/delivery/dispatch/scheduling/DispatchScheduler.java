package com.delivery.dispatch.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Розклад фонових процесів Dispatch; у тестах вимикається, щоб керувати часом вручну. */
@Component
@ConditionalOnProperty(prefix = "dispatch.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DispatchScheduler {

    private final DispatchQueueProcessor processor;

    public DispatchScheduler(DispatchQueueProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${dispatch.scheduling.expiry-check-interval:5s}")
    public void expireOffers() {
        processor.expireOffers();
    }

    @Scheduled(fixedDelayString = "${dispatch.scheduling.retry-interval:30s}")
    public void retryWaitingShipments() {
        processor.processQueue();
    }
}
