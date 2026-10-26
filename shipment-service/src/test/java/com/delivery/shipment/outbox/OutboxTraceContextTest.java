package com.delivery.shipment.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTraceContextTest {

    private final OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    private final io.opentelemetry.api.trace.Tracer otelTracer = otel.getTracer("test");
    private final Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> { });
    private final OutboxTraceContext traceContext = new OutboxTraceContext(
            tracer, new OtelPropagator(otel.getPropagators(), otelTracer));

    @Test
    void captureWithoutActiveSpanReturnsNull() {
        assertThat(traceContext.capture()).isNull();
    }

    @Test
    void relaySpanContinuesTraceOfCapturedContext() {
        Span request = tracer.nextSpan().name("POST /shipments").start();
        String traceparent;
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            traceparent = traceContext.capture();
        }
        request.end();

        // відкладене надсилання: активного спану немає, контекст береться лише з outbox
        Span relay = traceContext.startChild("outbox relay publish", traceparent);

        assertThat(traceparent).startsWith("00-" + request.context().traceId());
        assertThat(relay.context().traceId()).isEqualTo(request.context().traceId());
        assertThat(relay.context().parentId()).isEqualTo(request.context().spanId());
        relay.end();
    }
}
