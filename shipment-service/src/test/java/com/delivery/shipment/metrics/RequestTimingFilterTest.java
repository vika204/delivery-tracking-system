package com.delivery.shipment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTimingFilterTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final RequestTimingFilter filter = new RequestTimingFilter(registry);

    @Test
    void recordsTimerWithMatchedPatternInsteadOfRawPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shipments/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/shipments/{id}");
                ((MockHttpServletResponse) res).setStatus(200);
            }
        };

        filter.doFilter(request, response, chain);

        var timer = registry.get("delivery.http.request")
                .tag("method", "GET").tag("uri", "/shipments/{id}").tag("status", "200").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void skipsActuatorAndInternalEndpoints() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/prometheus"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/internal/chaos/fail"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/shipments"))).isFalse();
    }
}
