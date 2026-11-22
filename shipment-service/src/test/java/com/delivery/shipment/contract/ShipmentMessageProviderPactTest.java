package com.delivery.shipment.contract;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.outbox.ShipmentEventOutbox;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

/**
 * Verifies the Kafka message pacts of consumers of shipment-service (notification, tracking, dispatch). Each message
 * is produced by the real {@link ShipmentEventOutbox}, so the JSON is exactly what the outbox relay would publish.
 */
@Provider("shipment-service")
@PactFolder("../pacts/messages")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=false"
})
@Testcontainers
class ShipmentMessageProviderPactTest {

    static {
        System.setProperty("pact_do_not_track", "true");
    }

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ShipmentEventOutbox eventOutbox;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
        jdbcTemplate.execute("TRUNCATE TABLE shipments, shipment_status_history, outbox_events RESTART IDENTITY CASCADE");
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a SHIPMENT_CREATED event")
    String shipmentCreated() {
        eventOutbox.shipmentCreated(shipment(ShipmentStatus.WAITING_FOR_COURIER));
        return onlyPayload();
    }

    @PactVerifyProvider("a SHIPMENT_DETAILS_UPDATED event")
    String shipmentDetailsUpdated() {
        eventOutbox.detailsUpdated(shipment(ShipmentStatus.WAITING_FOR_COURIER), true);
        return onlyPayload();
    }

    @PactVerifyProvider("a SHIPMENT_STATUS_CHANGED event to DELIVERED")
    String statusChangedToDelivered() {
        eventOutbox.statusChanged(shipment(ShipmentStatus.DELIVERED), ShipmentStatus.IN_TRANSIT,
                10L, "Andrii Kovalenko", ActorType.COURIER, null);
        return onlyPayload();
    }

    @PactVerifyProvider("a SHIPMENT_STATUS_CHANGED event to IN_TRANSIT")
    String statusChangedToInTransit() {
        eventOutbox.statusChanged(shipment(ShipmentStatus.IN_TRANSIT), ShipmentStatus.ASSIGNED,
                10L, "Andrii Kovalenko", ActorType.COURIER, null);
        return onlyPayload();
    }

    private String onlyPayload() {
        return outboxRepository.findAll().getFirst().getPayload();
    }

    private Shipment shipment(ShipmentStatus status) {
        return shipmentRepository.saveAndFlush(Shipment.builder()
                .userId(42L)
                .senderName("Olena Kovalenko")
                .senderPhone("+380501234567")
                .recipientName("Marta Shevchenko")
                .recipientPhone("+380671234567")
                .pickupAddress("Kyiv, Khreshchatyk 1")
                .pickupCityCode("UA80000000000093317")
                .pickupLatitude(50.4501)
                .pickupLongitude(30.5234)
                .deliveryAddress("Lviv, Svobody 10")
                .deliveryCityCode("UA46060250010015970")
                .deliveryLatitude(49.8397)
                .deliveryLongitude(24.0297)
                .weight(new BigDecimal("2.50"))
                .length(new BigDecimal("30.00"))
                .width(new BigDecimal("20.00"))
                .height(new BigDecimal("15.00"))
                .distance(new BigDecimal("540.00"))
                .price(new BigDecimal("885.00"))
                .status(status)
                .build());
    }
}
