package com.delivery.dispatch.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.dispatch.dto.AssignmentResponse;
import com.delivery.dispatch.dto.CourierStatusResponse;
import com.delivery.dispatch.dto.OfferResponse;
import com.delivery.dispatch.dto.ShipmentCourierResponse;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierAvailability;
import com.delivery.dispatch.entity.OfferStatus;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.dispatch.service.ShipmentCourierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Provider("dispatch-service")
@PactFolder("../pacts")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=localhost:9999",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false",
                "outbox.relay.enabled=false",
                "dispatch.scheduling.enabled=false",
                "dispatch.courier-lookup.enabled=true"
        })
@Testcontainers
class DispatchProviderPactTest {

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

    @MockitoBean
    private CourierService courierService;
    @MockitoBean
    private DispatchService dispatchService;
    @MockitoBean
    private ShipmentCourierService shipmentCourierService;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("courier 10 exists")
    void courier10Exists() {
        Courier courier = Courier.builder()
                .userId(10L)
                .displayName("Andrii Kovalenko")
                .phone("+380501234567")
                .workZone("Kyiv")
                .workZoneCode("UA80000000000093317")
                .maxPackageWeight(new BigDecimal("15.5"))
                .isAvailable(true)
                .build();
        when(courierService.get(10L)).thenReturn(courier);
        when(courierService.update(eq(10L), any())).thenReturn(courier);
    }

    @State("courier 10 is online")
    void courier10IsOnline() {
        when(courierService.status(10L)).thenReturn(
                new CourierStatusResponse(10, true, CourierAvailability.AVAILABLE, 0, null));
        when(courierService.setAvailability(10L, false)).thenReturn(
                new CourierStatusResponse(10, false, CourierAvailability.UNAVAILABLE, 0, null));
    }

    @State("courier 10 has a pending offer 55")
    void courier10HasPendingOffer() {
        when(courierService.currentOffer(10L)).thenReturn(Optional.of(new OfferResponse(
                55, 15, OfferStatus.PENDING, LocalDateTime.of(2026, 11, 22, 12, 0), 60,
                "Kyiv, Khreshchatyk 1", "Lviv, Svobody 10",
                new BigDecimal("2.5"), new BigDecimal("540.0"), 1.2, new BigDecimal("885.0"), Instant.now())));
        when(dispatchService.acceptOffer(10L, 55L)).thenReturn(Assignment.builder().assignmentId(3L).shipmentId(15L).build());
        when(courierService.describe(any())).thenReturn(assignment(AssignmentStatus.ACTIVE));
    }

    @State("courier 10 has no pending offer")
    void courier10HasNoOffer() {
        when(courierService.currentOffer(10L)).thenReturn(Optional.empty());
    }

    @State("courier 10 has active assignment 3")
    void courier10HasAssignment() {
        when(courierService.assignments(10L, true)).thenReturn(List.of(assignment(AssignmentStatus.ACTIVE)));
    }

    @State("shipment 15 has courier Andrii")
    void shipment15HasCourier() {
        when(shipmentCourierService.getCurrentCourier(15L))
                .thenReturn(new ShipmentCourierResponse(15L, true, "Andrii Kovalenko"));
    }

    @State("shipment 16 has no courier")
    void shipment16HasNoCourier() {
        when(shipmentCourierService.getCurrentCourier(16L)).thenReturn(new ShipmentCourierResponse(16L, false, null));
    }

    private static AssignmentResponse assignment(AssignmentStatus status) {
        return new AssignmentResponse(3, 15, status, false, Instant.now(), null, null, null,
                "Kyiv, Khreshchatyk 1", "Lviv, Svobody 10",
                new BigDecimal("2.5"), new BigDecimal("540.0"), new BigDecimal("885.0"));
    }
}
