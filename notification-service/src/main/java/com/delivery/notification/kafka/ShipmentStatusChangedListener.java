package com.delivery.notification.kafka;

import com.delivery.notification.event.ShipmentStatusChangedEvent;
import com.delivery.notification.exception.InvalidShipmentEventException;
import com.delivery.notification.metrics.NotificationMetrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Component
public class ShipmentStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusChangedListener.class);
    private final IdempotentShipmentEventHandler handler;
    private final NotificationMetrics metrics;

    public ShipmentStatusChangedListener(IdempotentShipmentEventHandler handler, NotificationMetrics metrics) {
        this.handler = handler;
        this.metrics = metrics;
    }

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts}",
            backOff = @BackOff(delayString = "${app.kafka.retry.delay-ms}",
                    multiplierString = "${app.kafka.retry.multiplier}", maxDelayString = "${app.kafka.retry.max-delay-ms}"),
            exclude = InvalidShipmentEventException.class,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "shipment.status.changed", groupId = "shipment-status-notification-group")
    public void listen(ShipmentStatusChangedEvent event) {
        metrics.timeProcessing(() -> {
            String violations = EventValidator.violations(event);
            if (violations != null) {
                throw new InvalidShipmentEventException("Shipment status event is invalid: " + violations);
            }
            handler.handle(event);
        });
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, byte[]> record) {
        metrics.deadLettered();
        log.error("Shipment status notification moved to DLT: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
    }
}
