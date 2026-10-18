package com.delivery.shipment.service;

import com.delivery.shipment.chaos.ChaosFailureException;
import com.delivery.shipment.chaos.ChaosSettings;
import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.entity.IdempotencyRecord;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.exception.IdempotencyKeyReuseException;
import com.delivery.shipment.metrics.ShipmentMetrics;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipmentServiceMetricsTest {

    private MeterRegistry registry;
    private ChaosSettings chaos;
    private ShipmentRepository shipmentRepository;
    private IdempotencyRecordRepository idempotencyRepository;
    private ShipmentService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        chaos = new ChaosSettings();
        shipmentRepository = mock(ShipmentRepository.class);
        idempotencyRepository = mock(IdempotencyRecordRepository.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        TransactionTemplate tx = new TransactionTemplate(new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) { }
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) { }
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) { }
        });
        service = new ShipmentService(shipmentRepository, idempotencyRepository, outboxRepository,
                JsonMapper.builder().build(), tx, new ShipmentMetrics(registry, outboxRepository), chaos);
    }

    @Test
    void successfulCreationIncrementsCreatedAndRecordsWeight() {
        when(idempotencyRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(shipmentRepository.save(any())).thenAnswer(i -> {
            Shipment s = i.getArgument(0);
            s.setShipmentId(1L);
            return s;
        });

        service.create("k", request());

        assertThat(count("delivery.shipments.registered")).isEqualTo(1);
        assertThat(count("delivery.shipments.failed")).isZero();
        assertThat(registry.get("delivery.shipments.weight").summary().totalAmount()).isEqualTo(4.0);
    }

    @Test
    void unexpectedErrorIncrementsFailed() {
        chaos.setFailureRate(1.0);

        assertThatThrownBy(() -> service.create("k", request())).isInstanceOf(ChaosFailureException.class);

        assertThat(count("delivery.shipments.failed")).isEqualTo(1);
        assertThat(count("delivery.shipments.registered")).isZero();
    }

    @Test
    void idempotencyKeyReuseIsAClientErrorAndNotCountedAsFailure() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey("k").requestHash("different").shipmentId(1L).build();
        when(idempotencyRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.create("k", request())).isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(count("delivery.shipments.failed")).isZero();
    }

    @Test
    void replayIsNotCountedAsNewShipment() {
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(Shipment.builder().shipmentId(1L).build()));
        when(idempotencyRepository.findByIdempotencyKey("k")).thenAnswer(i -> Optional.of(
                IdempotencyRecord.builder().idempotencyKey("k").shipmentId(1L)
                        .requestHash(hashOf(request())).build()));

        service.create("k", request());

        assertThat(count("delivery.shipments.registered")).isZero();
    }

    private double count(String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    private String hashOf(CreateShipmentRequest request) {
        try {
            var m = ShipmentService.class.getDeclaredMethod("hash", CreateShipmentRequest.class);
            m.setAccessible(true);
            return (String) m.invoke(service, request);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CreateShipmentRequest request() {
        return new CreateShipmentRequest(1L, "Name", "+380000000000", "A", "B",
                new BigDecimal("4"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.TEN, ShipmentStatus.values()[0]);
    }
}
