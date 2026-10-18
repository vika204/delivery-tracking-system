package com.delivery.shipment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Records the latency of business endpoints as {@code delivery_http_request_seconds}
 * with a percentile histogram, which the HighLatency alert queries.
 */
@Component
public class RequestTimingFilter extends OncePerRequestFilter {

    static final String METRIC = "delivery.http.request";

    private final MeterRegistry registry;

    public RequestTimingFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Timer.builder(METRIC)
                    .description("Latency of business HTTP endpoints")
                    .tag("method", request.getMethod())
                    .tag("uri", pattern != null ? pattern.toString() : "UNKNOWN")
                    .tag("status", String.valueOf(response.getStatus()))
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
