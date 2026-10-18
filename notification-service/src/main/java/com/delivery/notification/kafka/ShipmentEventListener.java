package com.delivery.notification.kafka;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.notification.handler.ShipmentEventHandler;
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

    // Повторювані помилки проходять через shipment.events-retry-N із зростаючою затримкою.
    // Неповторювані (зіпсований JSON, порушення контракту) одразу йдуть у shipment.events-dlt.
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
    public void handleDlt(ConsumerRecord<String, byte[]> record) {
        metrics.deadLettered();
        log.error(
                "Shipment event moved to DLT: topic={}, partition={}, offset={}, key={}, originalTopic={}, exception={}, message={}, payload={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                header(record, KafkaHeaders.ORIGINAL_TOPIC),
                header(record, KafkaHeaders.EXCEPTION_CAUSE_FQCN),
                header(record, KafkaHeaders.EXCEPTION_MESSAGE),
                record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8)
        );
    }

    private void validate(ShipmentCreatedEvent event) {
        if (event.eventId() == null) {
            throw new InvalidShipmentEventException("Shipment event has no eventId");
        }
        if (event.payload() == null || event.payload().shipmentId() == null) {
            throw new InvalidShipmentEventException("Shipment event " + event.eventId() + " has no shipmentId");
        }
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
