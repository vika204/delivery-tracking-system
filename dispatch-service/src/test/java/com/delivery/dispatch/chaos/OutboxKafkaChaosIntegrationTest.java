package com.delivery.dispatch.chaos;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.OutboxEvent;
import com.delivery.dispatch.outbox.DispatchEventOutbox;
import com.delivery.dispatch.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Chaos for dispatch-service's outbox: a failed send must not let later events of the same shipment overtake it. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=true",
        "outbox.relay.delay-ms=150",
        "dispatch.scheduling.enabled=false"
})
@Testcontainers
class OutboxKafkaChaosIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private final List<String> acknowledgedPayloads = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failNextSendOnly = new AtomicBoolean();

    @MockitoBean
    private KafkaTemplate<String, String> kafka;

    @Autowired private DispatchEventOutbox eventOutbox;
    @Autowired private OutboxEventRepository outbox;

    @BeforeEach
    void setUp() {
        acknowledgedPayloads.clear();
        failNextSendOnly.set(false);
        outbox.deleteAll();
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            if (failNextSendOnly.compareAndSet(true, false)) {
                return CompletableFuture.failedFuture(new org.apache.kafka.common.errors.NetworkException("one lost send"));
            }
            acknowledgedPayloads.add(call.getArgument(2));
            return CompletableFuture.completedFuture(null);
        });
    }

    @Test
    void aFailedEventIsNotOvertakenByALaterEventOfTheSameShipment() {
        failNextSendOnly.set(true);
        eventOutbox.courierAssigned(assignment(15L));
        eventOutbox.courierUnassigned(assignment(15L), "COURIER_WITHDREW");
        List<String> createdOrder = outbox.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
                .map(event -> event.getEventId().toString())
                .toList();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());

        List<String> delivered = acknowledgedPayloads.stream()
                .map(payload -> createdOrder.stream().filter(payload::contains).findFirst().orElseThrow())
                .toList();
        assertThat(delivered).as("delivery order equals creation order").isEqualTo(createdOrder);
    }

    @Test
    void aFailureOnOneShipmentDoesNotBlockOtherShipments() {
        failNextSendOnly.set(true);
        eventOutbox.courierAssigned(assignment(15L));
        eventOutbox.courierAssigned(assignment(16L));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());
        assertThat(acknowledgedPayloads).hasSize(2);
    }

    private static Assignment assignment(long shipmentId) {
        return Assignment.builder()
                .assignmentId(3L)
                .shipmentId(shipmentId)
                .courier(Courier.builder().userId(10L).displayName("Andrii Kovalenko").build())
                .status(AssignmentStatus.ACTIVE)
                .build();
    }
}
