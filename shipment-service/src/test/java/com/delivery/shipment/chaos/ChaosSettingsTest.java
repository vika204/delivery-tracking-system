package com.delivery.shipment.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaosSettingsTest {

    private final ChaosSettings settings = new ChaosSettings();

    @Test
    void isInertByDefault() {
        assertThatCode(settings::inject).doesNotThrowAnyException();
    }

    @Test
    void alwaysFailsAtRateOne() {
        settings.setFailureRate(1.0);

        assertThatThrownBy(settings::inject).isInstanceOf(ChaosFailureException.class);
    }

    @Test
    void injectsLatency() {
        settings.setLatencyMs(80);

        long start = System.nanoTime();
        settings.inject();

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(70);
    }

    @Test
    void resetRestoresDefaults() {
        settings.setFailureRate(1.0);
        settings.setLatencyMs(500);

        settings.reset();

        assertThat(settings.failureRate()).isZero();
        assertThat(settings.latencyMs()).isZero();
        assertThatCode(settings::inject).doesNotThrowAnyException();
    }
}
