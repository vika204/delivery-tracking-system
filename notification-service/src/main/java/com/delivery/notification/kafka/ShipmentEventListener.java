package com.delivery.notification.kafka;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.notification.handler.ShipmentEventHandler;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventListener {

    public static final String TOPIC = "shipment.events";

    private final ShipmentEventHandler handler;

    public ShipmentEventListener(ShipmentEventHandler handler) {
        this.handler = handler;
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
        validate(event);
        handler.handle(event);
    }

    private void validate(ShipmentCreatedEvent event) {
        if (event.eventId() == null) {
            throw new InvalidShipmentEventException("Shipment event has no eventId");
        }
        if (event.payload() == null || event.payload().shipmentId() == null) {
            throw new InvalidShipmentEventException("Shipment event " + event.eventId() + " has no shipmentId");
        }
    }
}
