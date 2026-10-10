package com.delivery.shipment.outbox;

import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.repository.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    public void publishPendingEvents() {

        List<OutboxEvent> events = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        KafkaTopicConfig.SHIPMENT_EVENTS_TOPIC,
                        event.getAggregateId(),
                        event.getPayload()
                ).get(10, TimeUnit.SECONDS);

                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());

                outboxRepository.saveAndFlush(event);

                log.info("Outbox event published: {}", event.getEventId());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted");
                return;

            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getEventId(), e);
            }
        }
    }
}
