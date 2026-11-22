package com.delivery.tracking.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.tracking.client.ShipmentClient;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.client.dto.ShipmentSummary;
import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.LocationRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the HTTP pacts addressed to tracking-service. Location history runs against a real database; the
 * shipment-service client is stubbed because that hop has its own contract (tracking-service -> shipment-service).
 */
@Provider("tracking-service")
@PactFolder("../pacts")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.grpc.server.port=0",
                "tracking.location-history.enabled=true"
        })
@Testcontainers
class TrackingProviderPactTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingRepository locations;

    @MockitoBean
    private ShipmentClient shipmentClient;

    @MockitoBean
    private LocationRecordingService recordingService;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
        locations.deleteAll();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("shipment 1 is known to shipment-service")
    void shipment1IsKnown() {
        when(shipmentClient.getShipmentById(1L))
                .thenReturn(new ShipmentDto(1L, "Marta Shevchenko", "WAITING_FOR_COURIER"));
        when(shipmentClient.getShipmentsBatch(anyList())).thenReturn(new ShipmentBatchResponse(
                List.of(new ShipmentSummary(1L, "WAITING_FOR_COURIER", "Kyiv, Khreshchatyk 1", "Lviv, Svobody 10")),
                List.of()));
    }

    @State("shipment 404 is unknown to shipment-service")
    void shipment404IsUnknown() {
        when(shipmentClient.getShipmentById(404L))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
    }

    @State("shipment 15 has recorded locations")
    void shipment15HasLocations() {
        locations.saveAndFlush(Location.builder()
                .shipmentId(15L)
                .latitude(new BigDecimal("50.4501"))
                .longitude(new BigDecimal("30.5234"))
                .recordedAt(LocalDateTime.of(2026, 11, 12, 12, 2))
                .build());
    }

    @State("shipment 15 accepts locations")
    void shipment15AcceptsLocations() {
        when(recordingService.recordLocation(eq(15L), any())).thenReturn(new LocationRecordingService.Recorded(
                new ShipmentLocationResponse(1L, 15L, new BigDecimal("50.4501"), new BigDecimal("30.5234"),
                        LocalDateTime.of(2026, 11, 12, 12, 2)),
                false));
    }
}
