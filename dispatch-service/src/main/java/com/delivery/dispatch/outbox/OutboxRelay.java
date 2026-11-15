package com.delivery.dispatch.outbox;

import com.delivery.dispatch.config.KafkaTopicConfig;
import com.delivery.dispatch.entity.OutboxEvent;
import com.delivery.dispatch.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Публікує події з outbox у Kafka. Ключ повідомлення — shipmentId, тож події однієї доставки
 * потрапляють в одну партицію й читаються Shipment у порядку створення.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxTraceContext traceContext;
    private final Clock clock;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                       OutboxTraceContext traceContext, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.traceContext = traceContext;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc()) {
            UUID eventId = event.getEventId();
            if (!inFlight.add(eventId)) {
                continue;
            }

            Span span = traceContext.startChild("outbox relay publish", event.getTraceparent());
            try (Tracer.SpanInScope ignored = traceContext.activate(span)) {
                kafkaTemplate.send(KafkaTopicConfig.DISPATCH_EVENTS_TOPIC, event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            try {
                                if (ex != null) {
                                    log.error("Failed to publish outbox event: {}", eventId, ex);
                                    return;
                                }
                                if (outboxRepository.markPublished(eventId, Instant.now(clock)) == 1) {
                                    log.info("Outbox event published: eventId={}, type={}, shipmentId={}",
                                            eventId, event.getEventType(), event.getAggregateId());
                                }
                            } catch (Exception e) {
                                log.error("Failed to update outbox event: {}", eventId, e);
                            } finally {
                                inFlight.remove(eventId);
                            }
                        });
            } catch (Exception e) {
                inFlight.remove(eventId);
                span.error(e);
                log.error("Failed to send outbox event: {}", eventId, e);
            } finally {
                span.end();
            }
        }
    }
}
