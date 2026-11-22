package com.delivery.shipment.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.geo.OfflineRoutingClient;
import com.delivery.shipment.geo.RoutingClient;
import com.delivery.shipment.repository.PriceQuoteRepository;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Verifies every consumer pact addressed to shipment-service (tracking-service, web-ui) against the running
 * application and a real database. Each provider state starts from an empty database and seeds shipment id 1.
 */
@Provider("shipment-service")
@PactFolder("../pacts")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=localhost:9999",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false",
                "outbox.relay.enabled=false"
        }
)
@Testcontainers
class ShipmentProviderPactTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private PriceQuoteRepository quoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestBean
    private RoutingClient routingClient;

    static RoutingClient routingClient() {
        return new OfflineRoutingClient();
    }

    @BeforeEach
    void setUp(PactVerificationContext context) {
        if (context != null) {
            context.setTarget(new HttpTestTarget("localhost", port));
        }
        cleanDatabase();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE shipments, shipment_status_history, price_quotes, idempotency_records, "
                + "outbox_events, processed_events RESTART IDENTITY CASCADE");
    }

    @State({"shipment 1 exists", "user 42 has waiting shipment 1"})
    void shipment1Exists() {
        saveShipment(ShipmentStatus.WAITING_FOR_COURIER, null);
    }

    @State("shipment 404 does not exist")
    void shipment404DoesNotExist() {
        // empty database
    }

    @State("shipment 1 is assigned to courier 7")
    void shipment1IsAssigned() {
        saveShipment(ShipmentStatus.ASSIGNED, 7L);
    }

    @State("shipment 1 is in transit with courier 7")
    void shipment1IsInTransit() {
        saveShipment(ShipmentStatus.IN_TRANSIT, 7L);
    }

    @State("routing can geocode Kyiv and Lviv")
    void routingCanGeocode() {
        // OfflineRoutingClient knows the settlements used by the pacts
    }

    @State("a usable price quote exists for user 42")
    void usableQuoteExists() {
        Instant now = Instant.now();
        quoteRepository.saveAndFlush(PriceQuote.builder()
                .quoteId(UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2"))
                .userId(42L)
                .pickupAddress("Kyiv, Khreshchatyk 1")
                .pickupCityCode("UA80000000000093317")
                .pickupLatitude(50.4501)
                .pickupLongitude(30.5234)
                .deliveryAddress("Lviv, Svobody 10")
                .deliveryCityCode("UA46060250010015970")
                .deliveryLatitude(49.8397)
                .deliveryLongitude(24.0297)
                .distanceKm(new BigDecimal("540.00"))
                .weight(new BigDecimal("2.50"))
                .basePrice(new BigDecimal("50.00"))
                .pricePerKm(new BigDecimal("1.50"))
                .pricePerKg(new BigDecimal("10.00"))
                .distanceCharge(new BigDecimal("810.00"))
                .weightCharge(new BigDecimal("25.00"))
                .totalPrice(new BigDecimal("885.00"))
                .calculatedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .build());
    }

    private void saveShipment(ShipmentStatus status, Long courierId) {
        Instant now = Instant.now();
        shipmentRepository.saveAndFlush(Shipment.builder()
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
                .assignedCourierId(courierId)
                .assignedCourierName(courierId == null ? null : "Andrii Kovalenko")
                .assignedAt(courierId == null ? null : now)
                .pickedUpAt(status == ShipmentStatus.IN_TRANSIT ? now : null)
                .build());
    }
}
