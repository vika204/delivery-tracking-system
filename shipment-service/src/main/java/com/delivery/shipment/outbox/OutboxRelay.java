package com.delivery.shipment.outbox;

import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.event.ShipmentCreatedEvent;
import com.delivery.shipment.event.ShipmentStatusChangedEvent;
import com.delivery.shipment.repository.OutboxEventRepository;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxTraceContext traceContext;
    private final LongTaskTimer batchTimer;
    private final Clock clock;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                       MeterRegistry registry, OutboxTraceContext traceContext, Clock clock) {
        this.traceContext = traceContext;
        this.clock = clock;
        this.outboxRepository = outboxRepository;
        this.batchTimer = LongTaskTimer.builder("delivery.outbox.relay.batch")
                .description("Outbox relay publishing cycles currently in progress")
                .register(registry);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    public void publishPendingEvents() {
        batchTimer.record(this::publishBatch);
    }

    /**
     * Publishes pending events oldest first. Each send is awaited so a failure is known before the next event goes
     * out: once an event fails, every later event with the same key (= same partition) is held back until the next
     * cycle, which retries the failed one first. Without this a retried older event would reach Kafka after a newer
     * one and consumers would apply stale state. Events of other keys are unaffected.
     */
    private void publishBatch() {
        Set<String> blockedKeys = new HashSet<>();
        for (OutboxEvent event : outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc()) {
            if (blockedKeys.contains(event.getAggregateId())) {
                continue;
            }
            if (!publish(event)) {
                blockedKeys.add(event.getAggregateId());
            }
        }
    }

    private boolean publish(OutboxEvent event) {
        UUID eventId = event.getEventId();
        Span span = traceContext.startChild("outbox relay publish", event.getTraceparent());
        try (var _ = traceContext.activate(span)) {
            kafkaTemplate.send(topicFor(event), event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (outboxRepository.markPublished(eventId, LocalDateTime.now(clock)) == 1) {
                log.info("Outbox event published: {}", eventId);
            } else {
                log.warn("Outbox event already published: {}", eventId);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            span.error(e);
            log.error("Interrupted while publishing outbox event: {}", eventId, e);
            return false;
        } catch (Exception e) {
            span.error(e);
            log.error("Failed to publish outbox event: {}", eventId, e);
            return false;
        } finally {
            span.end();
        }
    }

    static String topicFor(OutboxEvent event) {
        return switch (event.getEventType()) {
            case ShipmentCreatedEvent.TYPE -> KafkaTopicConfig.SHIPMENT_EVENTS_TOPIC;
            case ShipmentStatusChangedEvent.TYPE -> KafkaTopicConfig.SHIPMENT_STATUS_CHANGED_TOPIC;
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + event.getEventType());
        };
    }
}
