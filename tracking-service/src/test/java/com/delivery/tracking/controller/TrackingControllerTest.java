package com.delivery.tracking.controller;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.exception.GlobalExceptionHandler;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TrackingControllerTest {

    private final TrackingRepository repository = mock(TrackingRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TrackingService trackingService = mock(TrackingService.class);
    private final MockMvc mvc = standaloneSetup(
            new TrackingController(new LocationService(repository, events), trackingService))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"quoteId\":\"3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f\",\"senderName\":\"Olena\","
                    + "\"senderPhone\":\"+380501234567\",\"recipientName\":\"Marta\",\"recipientPhone\":\"bad\","
                    + "\"length\":30,\"width\":20,\"height\":15}",
            "{\"quoteId\":\"3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f\",\"senderName\":\"Olena\","
                    + "\"senderPhone\":\"+380501234567\",\"recipientName\":\"Marta\",\"recipientPhone\":\"+380671234567\","
                    + "\"length\":0,\"width\":20,\"height\":15}"
    })
    void rejectsInvalidCreationBeforeCallingShipmentService(String body) throws Exception {
        mvc.perform(post("/tracking/shipments").header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:validation-failed"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        verifyNoInteractions(repository, events, trackingService);
    }

    @Test
    void readsLocationsWithoutCallingShipmentServiceOrPublishingEvents() throws Exception {
        when(repository.findAll()).thenReturn(List.of(location()));

        mvc.perform(get("/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locationId").value(7))
                .andExpect(jsonPath("$[0].shipmentId").value(15))
                .andExpect(jsonPath("$[0].latitude").value(50.45))
                .andExpect(jsonPath("$[0].longitude").value(30.52));
        verifyNoInteractions(trackingService, events);
    }

    @Test
    void readsLocationByIdWithExistingResponseFormat() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(location()));

        mvc.perform(get("/tracking/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(7))
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.recordedAt").exists());
        verifyNoInteractions(trackingService, events);
    }

    @Test
    void missingLocationStillReturns404() throws Exception {
        mvc.perform(get("/tracking/999"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(trackingService, events);
    }

    private static Location location() {
        return new Location(7L, 15L, new BigDecimal("50.45"), new BigDecimal("30.52"),
                LocalDateTime.of(2026, 11, 19, 10, 0));
    }
}
