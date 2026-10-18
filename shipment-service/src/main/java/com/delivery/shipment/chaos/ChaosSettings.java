package com.delivery.shipment.chaos;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime-adjustable fault injection used to trigger alerts on demand.
 * Both knobs default to zero, so the component is inert unless the chaos endpoints are enabled and used.
 */
@Component
public class ChaosSettings {

    private volatile double failureRate;
    private volatile long latencyMs;

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void reset() {
        this.failureRate = 0;
        this.latencyMs = 0;
    }

    public double failureRate() {
        return failureRate;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public void inject() {
        long delay = latencyMs;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new ChaosFailureException();
        }
    }
}
