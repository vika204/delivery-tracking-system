package com.delivery.dispatch.kafka;

import com.delivery.dispatch.config.KafkaTopicConfig;
import com.delivery.dispatch.event.ShipmentEventMessage;
import com.delivery.dispatch.event.ShipmentStatusChangedMessage;
import com.delivery.dispatch.exception.InvalidShipmentEventException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ShipmentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventsListener.class);

    private final JsonMapper jsonMapper;
    private final ShipmentEventProcessor processor;

    public ShipmentEventsListener(JsonMapper jsonMapper, ShipmentEventProcessor processor) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = KafkaTopicConfig.SHIPMENT_EVENTS_TOPIC,
            groupId = "dispatch-shipment-group")
    public void onShipmentEvent(String json) {
        ShipmentEventMessage event = parse(json, ShipmentEventMessage.class);
        if (event.eventId() == null || event.eventType() == null
                || event.payload() == null || event.payload().shipmentId() == null) {
            throw new InvalidShipmentEventException("Shipment event misses required fields: " + json, null);
        }
        log.info("Shipment event received: eventId={}, type={}, shipmentId={}",
                event.eventId(), event.eventType(), event.payload().shipmentId());
        processor.process(event);
    }

    @KafkaListener(topics = KafkaTopicConfig.SHIPMENT_STATUS_CHANGED_TOPIC, groupId = "dispatch-shipment-status-group")
    public void onShipmentStatusChanged(String json) {
        ShipmentStatusChangedMessage event = parse(json, ShipmentStatusChangedMessage.class);
        if (event.eventId() == null || event.shipmentId() == null || event.status() == null
                || !ShipmentStatusChangedMessage.TYPE.equals(event.eventType())) {
            throw new InvalidShipmentEventException("Shipment status event misses required fields: " + json, null);
        }
        log.info("Shipment status event received: eventId={}, shipmentId={}, status={}, courierId={}",
                event.eventId(), event.shipmentId(), event.status(), event.courierId());
        processor.process(event);
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new InvalidShipmentEventException("Malformed shipment event", ex);
        }
    }
}
