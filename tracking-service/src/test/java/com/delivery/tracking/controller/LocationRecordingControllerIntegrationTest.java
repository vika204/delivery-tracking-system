package com.delivery.tracking.controller;

import com.delivery.tracking.event.LocationSavedEvent;
import com.delivery.tracking.grpc.LocationStreamPublisher;
import com.delivery.tracking.repository.TrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Запис GPS-точки через REST: валідація, збереження й публікація підписникам gRPC-стріму. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@Testcontainers
class LocationRecordingControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.grpc.server.port", () -> "0");
        registry.add("tracking.location-history.enabled", () -> "true");
        registry.add("tracking.live.max-jump-meters", () -> "5000");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TrackingRepository trackingRepository;
    @MockitoSpyBean private LocationStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        trackingRepository.deleteAll();
    }

    @Test
    void savesValidPointAndPublishesItToStreamSubscribers() throws Exception {
        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        record(15, "50.450100", "30.523400", recordedAt)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").isNumber())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.latitude").value(50.4501));

        assertThat(trackingRepository.findAll()).singleElement()
                .satisfies(location -> assertThat(location.getShipmentId()).isEqualTo(15L));
        verify(publisher).onLocationSaved(argThat((LocationSavedEvent event) -> event.shipmentId() == 15L));
        mockMvc.perform(get("/tracking/shipments/15/locations"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        record(15, "91", "30.5", Instant.now())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:location-invalid"))
                .andExpect(jsonPath("$.detail").value("latitude must be between -90 and 90"));
        record(15, "50.4", "181", Instant.now()).andExpect(status().isBadRequest());

        assertNothingSaved();
    }

    @Test
    void rejectsMissingOrFutureTime() throws Exception {
        mockMvc.perform(post("/tracking/shipments/15/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 50.45, \"longitude\": 30.52}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("recordedAt is required"));
        record(15, "50.45", "30.52", Instant.now().plus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("recordedAt must not be in the future"));

        assertNothingSaved();
    }

    @Test
    void rejectsMissingCoordinatesAndInvalidShipment() throws Exception {
        mockMvc.perform(post("/tracking/shipments/15/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordedAt\": \"2026-11-19T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        record(0, "50.45", "30.52", Instant.now()).andExpect(status().isBadRequest());

        assertNothingSaved();
    }

    @Test
    void rejectsSuspiciousJumpFromPreviousPoint() throws Exception {
        record(15, "50.4501", "30.5234", Instant.now()).andExpect(status().isCreated());

        // Київ → Львів за одну точку
        record(15, "49.8397", "24.0297", Instant.now())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:location-suspicious-jump"));

        assertThat(trackingRepository.count()).isEqualTo(1);
    }

    @Test
    void rawLocationWriteWithoutValidationIsGone() throws Exception {
        mockMvc.perform(post("/tracking").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentId\": 15, \"latitude\": 999, \"longitude\": 999, \"recordedAt\": \"2026-11-19T10:00:00\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));

        assertNothingSaved();
    }

    private ResultActions record(long shipmentId, String latitude, String longitude, Instant recordedAt) throws Exception {
        return mockMvc.perform(post("/tracking/shipments/" + shipmentId + "/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"latitude": %s, "longitude": %s, "recordedAt": "%s"}
                        """.formatted(latitude, longitude, recordedAt)));
    }

    private void assertNothingSaved() {
        assertThat(trackingRepository.count()).isZero();
        verify(publisher, never()).onLocationSaved(any());
    }
}
