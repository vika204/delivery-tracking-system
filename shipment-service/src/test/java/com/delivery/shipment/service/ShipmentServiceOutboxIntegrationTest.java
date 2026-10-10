package com.delivery.shipment.service;

import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.outbox.OutboxRelay;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({ShipmentService.class, ShipmentServiceOutboxIntegrationTest.TestBeans.class})
class ShipmentServiceOutboxIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
        shipmentRepository.deleteAll();
    }

    @Test
    void shouldRollbackShipmentAndOutboxWhenOutboxSaveFails() {
        // given
        doThrow(new IllegalStateException("Outbox insert failed"))
                .when(outboxRepository)
                .save(any(OutboxEvent.class));

        // when
        assertThatThrownBy(() -> shipmentService.create("rollback-001", request()))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(shipmentRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepOutboxEventWhenKafkaFails() {
        // given
        var result = shipmentService.create("kafka-failure-001", request());
        String shipmentKey = result.shipment().getShipmentId().toString();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("shipment.events"), eq(shipmentKey), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new KafkaException("Kafka unavailable")));
        OutboxRelay relay = new OutboxRelay(outboxRepository, kafkaTemplate);

        // when
        relay.publishPendingEvents();

        // then
        assertThat(shipmentRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);

        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getAggregateId()).isEqualTo(shipmentKey);
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
    }

    private CreateShipmentRequest request() {
        return new CreateShipmentRequest(
                42L,
                "Olena Kovalenko",
                "+380501234567",
                "Kyiv, Khreshchatyk 1",
                "Lviv, Svobody 10",
                new BigDecimal("2.5"),
                new BigDecimal("30"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                new BigDecimal("540"),
                new BigDecimal("250"),
                ShipmentStatus.WAITING_FOR_COURIER
        );
    }
}
