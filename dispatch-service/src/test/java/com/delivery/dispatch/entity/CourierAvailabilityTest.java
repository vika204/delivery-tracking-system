package com.delivery.dispatch.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CourierAvailabilityTest {

    @ParameterizedTest(name = "online={0}, activeDelivery={1} -> {2}")
    @CsvSource({
            "true,  false, AVAILABLE",
            "false, false, UNAVAILABLE",
            "true,  true,  BUSY",
            "false, true,  BUSY"
    })
    void derivesStateFromToggleAndActiveDelivery(boolean online, boolean activeDelivery, CourierAvailability expected) {
        assertThat(CourierAvailability.of(online, activeDelivery)).isEqualTo(expected);
    }
}
