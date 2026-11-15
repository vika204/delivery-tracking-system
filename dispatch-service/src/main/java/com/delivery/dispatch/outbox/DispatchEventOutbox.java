package com.delivery.dispatch.outbox;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.entity.OutboxEvent;
import com.delivery.dispatch.events.DispatchEvent;
import com.delivery.dispatch.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Пише події Dispatch в outbox у поточній транзакції; {@link OutboxRelay} публікує їх у Kafka. */
@Component
public class DispatchEventOutbox {

    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final OutboxTraceContext traceContext;
    private final Clock clock;

    public DispatchEventOutbox(OutboxEventRepository outboxRepository, JsonMapper jsonMapper,
                               OutboxTraceContext traceContext, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.traceContext = traceContext;
        this.clock = clock;
    }

    public void courierAssigned(Assignment assignment) {
        publish(DispatchEvent.COURIER_ASSIGNED, assignment, null);
    }

    public void courierUnassigned(Assignment assignment, String reason) {
        publish(DispatchEvent.COURIER_UNASSIGNED, assignment, reason);
    }

    public void searchDelayed(DispatchRequest request, String reason) {
        Instant now = Instant.now(clock);
        save(new DispatchEvent(UUID.randomUUID(), DispatchEvent.COURIER_SEARCH_DELAYED, now,
                request.getShipmentId(), null, null, null, reason, request.getSenderId(), request.getCreatedAt()), now);
    }

    private void publish(String type, Assignment assignment, String reason) {
        Instant now = Instant.now(clock);
        save(new DispatchEvent(
                UUID.randomUUID(),
                type,
                now,
                assignment.getShipmentId(),
                assignment.getCourier().getUserId(),
                assignment.getCourier().getDisplayName(),
                assignment.getAssignmentId(),
                reason,
                null,
                null
        ), now);
    }

    private void save(DispatchEvent event, Instant now) {
        outboxRepository.save(OutboxEvent.builder()
                .eventId(event.eventId())
                .aggregateId(event.shipmentId().toString())
                .eventType(event.eventType())
                .payload(jsonMapper.writeValueAsString(event))
                .traceparent(traceContext.capture())
                .published(false)
                .createdAt(now)
                .build());
    }
}
