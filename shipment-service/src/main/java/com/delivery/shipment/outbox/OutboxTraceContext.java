package com.delivery.shipment.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OutboxTraceContext {

    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public String capture() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    public Span startChild(String name, String traceparent) {
        Span.Builder builder = traceparent == null
                ? tracer.spanBuilder()
                : propagator.extract(Map.of(TRACEPARENT, traceparent), Map<String, String>::get);
        return builder.name(name).start();
    }

    public Tracer.SpanInScope activate(Span span) {
        return tracer.withSpan(span);
    }
}
