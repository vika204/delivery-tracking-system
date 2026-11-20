package com.delivery.webui.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {

    @Test
    void acceptsPositiveValues() {
        RateLimitProperties properties = new RateLimitProperties(true, 1, Duration.ofMillis(1));

        assertThat(properties.capacity()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new RateLimitProperties(true, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacity");
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> new RateLimitProperties(true, 10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("window");
    }
}
