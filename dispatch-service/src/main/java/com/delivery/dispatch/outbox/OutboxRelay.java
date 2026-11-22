package com.delivery.dispatch.outbox;

import com.delivery.dispatch.config.KafkaTopicConfig;
import com.delivery.dispatch.entity.OutboxEvent;
import com.delivery.dispatch.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private final Clock clock;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                       OutboxTraceContext traceContext, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.traceContext = traceContext;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
    public void publishPendingEvents() {
        // Each send is awaited: once an event fails, later events with the same key (= same partition) wait for
        // the next cycle, which retries the failed one first. Otherwise a retried older event would be delivered
        // after a newer one.
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
            kafkaTemplate.send(KafkaTopicConfig.DISPATCH_EVENTS_TOPIC, event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (outboxRepository.markPublished(eventId, Instant.now(clock)) == 1) {
                log.info("Outbox event published: eventId={}, type={}, shipmentId={}",
                        eventId, event.getEventType(), event.getAggregateId());
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
}
