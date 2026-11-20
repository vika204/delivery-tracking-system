package com.delivery.dispatch.kafka;

import com.delivery.dispatch.entity.ProcessedEvent;
import com.delivery.dispatch.event.ShipmentEventMessage;
import com.delivery.dispatch.event.ShipmentStatusChangedMessage;
import com.delivery.dispatch.repository.ProcessedEventRepository;
import com.delivery.dispatch.service.DispatchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Обробка подій Shipment рівно один раз: позначка оброблення й зміни Dispatch — в одній транзакції. */
@Component
public class ShipmentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventProcessor.class);

    private final ProcessedEventRepository processedEventRepository;
    private final DispatchService dispatchService;
    private final Clock clock;

    public ShipmentEventProcessor(ProcessedEventRepository processedEventRepository,
                                  DispatchService dispatchService, Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    @Transactional
    public void process(ShipmentEventMessage event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        switch (event.eventType()) {
            case ShipmentEventMessage.SHIPMENT_CREATED -> dispatchService.onShipmentCreated(event.payload());
            case ShipmentEventMessage.SHIPMENT_DETAILS_UPDATED -> dispatchService.onShipmentDetailsUpdated(event.payload());
            default -> log.debug("Shipment event type ignored by dispatch: {}", event.eventType());
        }
    }

    @Transactional
    public void process(ShipmentStatusChangedMessage event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        dispatchService.onShipmentStatusChanged(event.shipmentId(), event.status(), event.courierId());
    }

    private boolean alreadyProcessed(UUID eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Shipment event already processed: eventId={}", eventId);
            return true;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now(clock)));
        return false;
    }
}
