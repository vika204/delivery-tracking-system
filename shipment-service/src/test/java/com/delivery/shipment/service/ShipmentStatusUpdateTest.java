package com.delivery.shipment.service;

import com.delivery.events.ShipmentStatusChangedEvent;
import com.delivery.shipment.chaos.ChaosSettings;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.exception.ShipmentStatusConflictException;
import com.delivery.shipment.metrics.ShipmentMetrics;
import com.delivery.shipment.outbox.OutboxTraceContext;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentStatusUpdateTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ShipmentMetrics metrics;
    @Mock
    private ChaosSettings chaos;
    @Mock
    private OutboxTraceContext traceContext;

    @InjectMocks
    private ShipmentService shipmentService;

    @Test
    void shouldSaveTerminalStatusAndOutboxEvent() {
        Shipment shipment = Shipment.builder().shipmentId(15L).status(ShipmentStatus.IN_TRANSIT).build();
        when(shipmentRepository.findById(15L)).thenReturn(Optional.of(shipment));
        when(jsonMapper.writeValueAsString(any(ShipmentStatusChangedEvent.class))).thenReturn("{}");

        Shipment updated = shipmentService.updateStatus(15L, ShipmentStatus.DELIVERED);

        assertThat(updated.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        verify(shipmentRepository).save(shipment);

        ArgumentCaptor<OutboxEvent> savedEvent = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(savedEvent.capture());
        assertThat(savedEvent.getValue().getEventType()).isEqualTo(ShipmentStatusChangedEvent.TYPE);
        assertThat(savedEvent.getValue().getAggregateId()).isEqualTo("15");
        assertThat(savedEvent.getValue().isPublished()).isFalse();
    }

    @Test
    void shouldNotPublishEventForUnchangedStatus() {
        Shipment shipment = Shipment.builder().shipmentId(15L).status(ShipmentStatus.DELIVERED).build();
        when(shipmentRepository.findById(15L)).thenReturn(Optional.of(shipment));

        assertThat(shipmentService.updateStatus(15L, ShipmentStatus.DELIVERED)).isSameAs(shipment);
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldRejectChangingAlreadyTerminalShipment() {
        Shipment shipment = Shipment.builder().shipmentId(15L).status(ShipmentStatus.CANCELLED).build();
        when(shipmentRepository.findById(15L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.updateStatus(15L, ShipmentStatus.IN_TRANSIT))
                .isInstanceOf(ShipmentStatusConflictException.class);
        verifyNoInteractions(outboxEventRepository);
    }
}