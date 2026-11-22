package com.delivery.shipment.chaos;

import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.outbox.ShipmentEventOutbox;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.apache.kafka.common.errors.TimeoutException;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Chaos experiments for the transactional outbox: the Kafka broker disappears while events are waiting to be
 * relayed. Steady state: every event reaches Kafka at least once after the broker returns, and nothing is marked
 * published that Kafka never acknowledged.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=true",
        "outbox.relay.delay-ms=150"
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

    /** Every acknowledged record, in broker order: "topic|key|payload". */
    private final List<String> acknowledged = new CopyOnWriteArrayList<>();
    private final AtomicBoolean brokerDown = new AtomicBoolean();
    private final AtomicBoolean brokerHangs = new AtomicBoolean();
    private final AtomicBoolean brokerFlaps = new AtomicBoolean();
    private final AtomicBoolean failNextSendOnly = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();

    @MockitoBean
    private KafkaTemplate<String, String> kafka;

    @Autowired private ShipmentEventOutbox eventOutbox;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private ShipmentRepository shipments;

    @BeforeEach
    void setUp() {
        brokerDown.set(false);
        brokerHangs.set(false);
        brokerFlaps.set(false);
        failNextSendOnly.set(false);
        attempts.set(0);
        acknowledged.clear();
        outbox.deleteAll();
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            attempts.incrementAndGet();
            if (brokerHangs.get()) {
                throw new TimeoutException("Topic not present in metadata after 60000 ms");
            }
            if (failNextSendOnly.compareAndSet(true, false)) {
                return CompletableFuture.failedFuture(new org.apache.kafka.common.errors.NetworkException("one lost send"));
            }
            if (brokerDown.get() || (brokerFlaps.get() && attempts.get() % 2 == 0)) {
                return CompletableFuture.failedFuture(new org.apache.kafka.common.errors.NetworkException("broker is gone"));
            }
            acknowledged.add(call.getArgument(0) + "|" + call.getArgument(1) + "|" + call.getArgument(2));
            return CompletableFuture.completedFuture(null);
        });
    }

    @Test
    void eventsWaitInTheOutboxWhileTheBrokerIsDownAndAreDeliveredOnceItReturns() {
        brokerDown.set(true);
        Shipment shipment = shipment();
        eventOutbox.shipmentCreated(shipment);
        changed(shipment);

        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 6);
        assertThat(acknowledged).as("nothing can be acknowledged by a dead broker").isEmpty();
        assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).as("nothing is lost or marked published").hasSize(2);

        brokerDown.set(false);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());
        assertThat(acknowledged).hasSize(2);
        assertThat(acknowledged).extracting(record -> record.split("\\|")[0])
                .containsExactlyInAnyOrder("shipment.events", "shipment.status.changed");
        assertThat(acknowledged).as("all records of one shipment share the shipment id as key")
                .allSatisfy(record -> assertThat(record.split("\\|")[1]).isEqualTo(String.valueOf(shipment.getShipmentId())));
    }

    @Test
    void aBrokerThatNeverAnswersDoesNotKillTheRelay() {
        brokerHangs.set(true);
        eventOutbox.shipmentCreated(shipment());

        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 3);
        assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).hasSize(1);

        brokerHangs.set(false);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());
        assertThat(acknowledged).hasSize(1);
    }

    @Test
    void aFailedEventIsNotOvertakenByALaterEventOnTheSameTopicAndKey() {
        Shipment shipment = shipment();
        failNextSendOnly.set(true);
        changed(shipment);
        changed(shipment);
        List<String> createdOrder = outbox.findAll().stream()
                .sorted(java.util.Comparator.comparing(OutboxEvent::getCreatedAt))
                .map(event -> event.getEventId().toString())
                .toList();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());

        // same topic + same key = same partition: consumers apply these in arrival order, so a stale
        // SHIPMENT_STATUS_CHANGED must never land after a newer one
        assertThat(acknowledged).hasSize(2);
        List<String> deliveredOrder = acknowledged.stream()
                .map(record -> createdOrder.stream().filter(record::contains).findFirst().orElseThrow())
                .toList();
        assertThat(deliveredOrder).as("delivery order equals creation order").isEqualTo(createdOrder);
    }

    @Test
    void anEventIsNeverPublishedTwiceOnceAcknowledged() throws Exception {
        eventOutbox.shipmentCreated(shipment());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());

        Thread.sleep(800); // several more relay cycles

        assertThat(acknowledged).hasSize(1);
        List<OutboxEvent> all = outbox.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().isPublished()).isTrue();
    }

    @Test
    void aFlappingBrokerStillDeliversEveryEventAtLeastOnceInTheEnd() {
        brokerFlaps.set(true);
        Shipment shipment = shipment();
        for (int i = 0; i < 10; i++) {
            changed(shipment);
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(outbox.findTop50ByPublishedFalseOrderByCreatedAtAsc()).isEmpty());
        assertThat(acknowledged).as("no event was lost").hasSize(10);
        assertThat(acknowledged).as("no event was delivered twice").doesNotHaveDuplicates();
    }

    private void changed(Shipment shipment) {
        eventOutbox.statusChanged(shipment, ShipmentStatus.WAITING_FOR_COURIER, null, null, ActorType.SYSTEM, null);
    }

    private Shipment shipment() {
        return shipments.saveAndFlush(Shipment.builder()
                .userId(42L)
                .senderName("Olena Kovalenko").senderPhone("+380501234567")
                .recipientName("Marta Shevchenko").recipientPhone("+380671234567")
                .pickupAddress("Kyiv, Khreshchatyk 1").pickupCityCode("UA80000000000093317")
                .pickupLatitude(50.4501).pickupLongitude(30.5234)
                .deliveryAddress("Lviv, Svobody 10").deliveryCityCode("UA46060250010015970")
                .deliveryLatitude(49.8397).deliveryLongitude(24.0297)
                .weight(new BigDecimal("2.50")).length(new BigDecimal("30.00"))
                .width(new BigDecimal("20.00")).height(new BigDecimal("15.00"))
                .distance(new BigDecimal("540.00")).price(new BigDecimal("885.00"))
                .status(ShipmentStatus.WAITING_FOR_COURIER)
                .build());
    }
}
