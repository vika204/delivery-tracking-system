package com.delivery.notification.handler;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.events.ShipmentPayload;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.ProcessedEvent;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class IdempotentShipmentEventHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
        registry.add("logging.level.org.apache.kafka", () -> "ERROR");
    }

    @Autowired
    private IdempotentShipmentEventHandler handler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private ShipmentCreatedEvent makeEvent(UUID eventId, long shipmentId) {
        return new ShipmentCreatedEvent(
                eventId,
                ShipmentCreatedEvent.TYPE,
                Instant.now(),
                new ShipmentPayload(shipmentId, 42L, "WAITING_FOR_COURIER")
        );
    }

    @Test
    void handle_newEvent_persistsNotificationAndProcessedEvent() {
        UUID id = UUID.randomUUID();

        handler.handle(makeEvent(id, 1L));

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getShipmentId()).isEqualTo(1L);
        assertThat(notifications.get(0).getUserId()).isEqualTo(42L);

        assertThat(processedEventRepository.existsById(id)).isTrue();
    }

    @Test
    void handle_sameEventIdTwice_producesExactlyOneNotification() {
        UUID id = UUID.randomUUID();
        ShipmentCreatedEvent event = makeEvent(id, 2L);

        handler.handle(event);
        handler.handle(event);

        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(processedEventRepository.findAll()).hasSize(1);
    }

    @Test
    void handle_twoDistinctEvents_persistsBoth() {
        handler.handle(makeEvent(UUID.randomUUID(), 3L));
        handler.handle(makeEvent(UUID.randomUUID(), 4L));

        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(processedEventRepository.findAll()).hasSize(2);
    }

    @Test
    void handle_eventIdAlreadyInDb_skipsCreatingNotification() {
        UUID id = UUID.randomUUID();
        processedEventRepository.save(new ProcessedEvent(id, Instant.now()));

        handler.handle(makeEvent(id, 5L));

        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(processedEventRepository.findAll()).hasSize(1);
    }
}
