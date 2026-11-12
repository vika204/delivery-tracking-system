package com.delivery.tracking.controller;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.TrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@Testcontainers
class ShipmentLocationControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("tracking_location_test_db")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.grpc.server.port", () -> "0");
        registry.add("tracking.location-history.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingRepository trackingRepository;

    @BeforeEach
    void setUp() {
        trackingRepository.deleteAll();
    }

    @Test
    void returnsOnlyRequestedShipmentLocationsOrderedByLocationId() throws Exception {

        Location first = saveLocation(
                15L,
                "50.4501",
                "30.5234",
                LocalDateTime.of(2026, 11, 12, 12, 2)
        );

        saveLocation(
                99L,
                "49.8397",
                "24.0297",
                LocalDateTime.of(2026, 11, 12, 12, 1)
        );

        Location second = saveLocation(
                15L,
                "50.4510",
                "30.5240",
                LocalDateTime.of(2026, 11, 12, 12, 0)
        );

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].locationId")
                        .value(first.getLocationId().intValue()))
                .andExpect(jsonPath("$[0].shipmentId")
                        .value(15))
                .andExpect(jsonPath("$[0].latitude")
                        .value(50.4501))
                .andExpect(jsonPath("$[0].longitude")
                        .value(30.5234))
                .andExpect(jsonPath("$[1].locationId")
                        .value(second.getLocationId().intValue()))
                .andExpect(jsonPath("$[1].shipmentId")
                        .value(15));
    }

    @Test
    void appliesAfterLocationIdAndLimit()
            throws Exception {

        Location first = saveLocation(
                15L,
                "50.4501",
                "30.5234",
                LocalDateTime.of(2026, 11, 12, 12, 0)
        );

        Location second = saveLocation(
                15L,
                "50.4510",
                "30.5240",
                LocalDateTime.of(2026, 11, 12, 12, 1)
        );

        saveLocation(
                15L,
                "50.4520",
                "30.5250",
                LocalDateTime.of(2026, 11, 12, 12, 2)
        );

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                                .param(
                                        "afterLocationId",
                                        first.getLocationId().toString()
                                )
                                .param("limit", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].locationId")
                        .value(second.getLocationId().intValue()))
                .andExpect(jsonPath("$[0].shipmentId")
                        .value(15));
    }

    @Test
    void returnsEmptyListWhenShipmentHasNoLocations()
            throws Exception {

        saveLocation(
                20L,
                "49.8397",
                "24.0297",
                LocalDateTime.of(2026, 11, 12, 12, 0)
        );

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsInvalidQueryParameters() throws Exception {

        mockMvc.perform(
                        get("/tracking/shipments/0/locations")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                                .param("afterLocationId", "-1")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/tracking/shipments/15/locations")
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());
    }

    private Location saveLocation(
            long shipmentId,
            String latitude,
            String longitude,
            LocalDateTime recordedAt
    ) {
        return trackingRepository.saveAndFlush(
                Location.builder()
                        .shipmentId(shipmentId)
                        .latitude(new BigDecimal(latitude))
                        .longitude(new BigDecimal(longitude))
                        .recordedAt(recordedAt)
                        .build()
        );
    }
}