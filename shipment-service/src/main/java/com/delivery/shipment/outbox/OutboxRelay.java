package com.delivery.shipment.outbox;

import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.repository.OutboxEventRepository;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LongTaskTimer batchTimer;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                       MeterRegistry registry) {
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

    private void publishBatch() {

        List<OutboxEvent> events = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            UUID eventId = event.getEventId();

            if (!inFlight.add(eventId)) {
                continue;
            }

            try {
                kafkaTemplate.send(
                        KafkaTopicConfig.SHIPMENT_EVENTS_TOPIC,
                        event.getAggregateId(),
                        event.getPayload()
                ).whenComplete((result, ex) -> {
                    try {
                        if (ex != null) {
                            log.error("Failed to publish outbox event: {}", eventId, ex);
                            return;
                        }

                        int updated = outboxRepository.markPublished(
                                eventId,
                                LocalDateTime.now()
                        );

                        if (updated == 1) {
                            log.info("Outbox event published: {}", eventId);
                        } else {
                            log.warn("Outbox event already published: {}", eventId);
                        }

                    } catch (Exception e) {
                        log.error("Failed to update outbox event: {}", eventId, e);
                    } finally {
                        inFlight.remove(eventId);
                    }
                });

            } catch (Exception e) {
                inFlight.remove(eventId);
                log.error("Failed to send outbox event: {}", eventId, e);
            }
        }
    }
}
