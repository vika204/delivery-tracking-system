package com.delivery.shipment.kafka;

import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.event.DispatchEvent;
import com.delivery.shipment.exception.InvalidDispatchEventException;
import com.delivery.shipment.service.ShipmentLifecycleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Dispatch -> Shipment: CourierAssigned / CourierUnassigned (Context Map). */
@Component
public class DispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventListener.class);

    private final JsonMapper jsonMapper;
    private final ShipmentLifecycleService lifecycleService;

    public DispatchEventListener(JsonMapper jsonMapper, ShipmentLifecycleService lifecycleService) {
        this.jsonMapper = jsonMapper;
        this.lifecycleService = lifecycleService;
    }

    @KafkaListener(topics = KafkaTopicConfig.DISPATCH_EVENTS_TOPIC, groupId = "shipment-dispatch-group")
    public void onDispatchEvent(String json) {
        DispatchEvent event = parse(json);
        if (!event.changesAssignment()) {
            // наприклад, COURIER_SEARCH_DELAYED: призначений для notification-service
            log.debug("Dispatch event not relevant for shipment: type={}, shipmentId={}", event.eventType(), event.shipmentId());
            return;
        }
        log.info("Dispatch event received: eventId={}, type={}, shipmentId={}, courierId={}",
                event.eventId(), event.eventType(), event.shipmentId(), event.courierId());
        lifecycleService.onDispatchEvent(event);
    }

    private DispatchEvent parse(String json) {
        DispatchEvent event;
        try {
            event = jsonMapper.readValue(json, DispatchEvent.class);
        } catch (JacksonException ex) {
            throw new InvalidDispatchEventException("Malformed dispatch event", ex);
        }
        if (event.eventId() == null || event.eventType() == null || event.shipmentId() == null
                || (event.changesAssignment() && event.courierId() == null)) {
            throw new InvalidDispatchEventException("Dispatch event misses required fields: " + json, null);
        }
        return event;
    }
}
