package com.delivery.shipment.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.shipment.entity.Shipment;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        if (context != null) {
            context.setTarget(new HttpTestTarget("localhost", port));
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @State("shipment 1 exists")
    void shipment1Exists() {
        jdbcTemplate.execute("TRUNCATE TABLE shipments RESTART IDENTITY CASCADE");

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
                .status(ShipmentStatus.WAITING_FOR_COURIER)
                .build());
    }
}
