package com.delivery.notification.kafka;

import com.delivery.notification.event.ShipmentCreatedEvent;
import com.delivery.notification.exception.InvalidShipmentEventException;
import com.delivery.notification.metrics.NotificationMetrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ShipmentEventListener {

    public static final String TOPIC = "shipment.events";
    public static final String DLT_TOPIC = TOPIC + "-dlt";

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventListener.class);

    private final ShipmentEventHandler handler;
    private final NotificationMetrics metrics;

    public ShipmentEventListener(ShipmentEventHandler handler, NotificationMetrics metrics) {
        this.handler = handler;
        this.metrics = metrics;
    }

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts}",
            backOff = @BackOff(
                    delayString = "${app.kafka.retry.delay-ms}",
                    multiplierString = "${app.kafka.retry.multiplier}",
                    maxDelayString = "${app.kafka.retry.max-delay-ms}"
            ),
            exclude = InvalidShipmentEventException.class,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = TOPIC,
            groupId = "shipment-notification-group"
    )
    public void listen(ShipmentCreatedEvent event) {
        metrics.timeProcessing(() -> {
            validate(event);
            handler.handle(event);
        });
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, byte[]> consumerRecord) {
        metrics.deadLettered();
        if (log.isErrorEnabled()) {
            log.error(
                    "Shipment event moved to DLT: topic={}, partition={}, offset={}, key={}, originalTopic={}, exception={}, message={}, payload={}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    consumerRecord.key(),
                    header(consumerRecord, KafkaHeaders.ORIGINAL_TOPIC),
                    header(consumerRecord, KafkaHeaders.EXCEPTION_CAUSE_FQCN),
                    header(consumerRecord, KafkaHeaders.EXCEPTION_MESSAGE),
                    consumerRecord.value() == null ? null : new String(consumerRecord.value(), StandardCharsets.UTF_8)
            );
        }
    }

    private void validate(ShipmentCreatedEvent event) {
        String violations = EventValidator.violations(event);
        if (violations != null) {
            throw new InvalidShipmentEventException("Shipment event is invalid: " + violations);
        }
    }

    private String header(ConsumerRecord<String, byte[]> consumerRecord, String name) {
        Header header = consumerRecord.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
