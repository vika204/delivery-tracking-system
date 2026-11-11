package com.delivery.webui.controller;

import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class TrackingPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingShipmentClient shipmentClient;

    @Test
    void contextLoads() {
    }

    @Test
    void showsNeutralTrackingPageWithoutShipmentId() throws Exception {
        mockMvc.perform(get("/tracking"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/tracking"))
                .andExpect(content().string(containsString("ON THE MOVE.")))
                .andExpect(content().string(containsString("name=\"shipmentId\"")))
                .andExpect(content().string(containsString("id=\"we-modal\"")))
                .andExpect(content().string(containsString("/css/components/modal.css")))
                .andExpect(content().string(containsString("/js/components/modal.js")))
                .andExpect(content().string(not(containsString("id=\"live-map\""))))
                .andExpect(content().string(not(containsString("class=\"tracking-card\""))))
                .andExpect(content().string(not(containsString("FOLLOW COURIER"))))
                .andExpect(content().string(not(containsString("SHOW ROUTE"))))
                .andExpect(content().string(not(containsString("2D VIEW"))))
                .andExpect(content().string(not(containsString("maplibre-gl.js"))))
                .andExpect(content().string(not(containsString("tracking-map.js"))))
                .andExpect(content().string(not(containsString(
                        "GPS controls will activate when coordinates are available."
                ))))
                .andExpect(content().string(not(containsString("DEMO TRACKING"))))
                .andExpect(content().string(not(containsString("TEST COORDINATES"))))
                .andExpect(content().string(not(containsString("PAUSE"))))
                .andExpect(content().string(not(containsString("REPLAY"))))
                .andExpect(content().string(not(containsString("WE-204-081"))))
                .andExpect(content().string(not(containsString("34.7787"))))
                .andExpect(content().string(not(containsString("id=\"pause\""))))
                .andExpect(content().string(not(containsString("id=\"restart\""))))
                .andExpect(content().string(not(containsString("id=\"progress\""))))
                .andExpect(content().string(not(containsString("tracking-demo.js"))));

        verifyNoInteractions(shipmentClient);
    }

    @Test
    void showsValidationErrorForInvalidShipmentId() throws Exception {
        mockMvc.perform(get("/tracking").param("shipmentId", "not-a-number"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Enter a numeric shipment ID, for example 15."
                )))
                .andExpect(content().string(containsString("data-we-modal-alert")))
                .andExpect(content().string(containsString("data-we-modal-title=\"TRACKING ERROR\"")))
                .andExpect(content().string(not(containsString("id=\"live-map\""))))
                .andExpect(content().string(not(containsString("class=\"tracking-card\""))))
                .andExpect(content().string(not(containsString("FOLLOW COURIER"))));

        verifyNoInteractions(shipmentClient);
    }

    @Test
    void showsRealShipmentStatusWithoutRecipientData() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        "Private Recipient",
                        "IN_TRANSIT"
                ));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"live-map\"")))
                .andExpect(content().string(containsString("class=\"tracking-card\"")))
                .andExpect(content().string(containsString("YOUR COURIER IS ON THE WAY")))
                .andExpect(content().string(containsString("Waiting for courier location")))
                .andExpect(content().string(containsString("Recipient information is not displayed.")))
                .andExpect(content().string(containsString("FOLLOW COURIER")))
                .andExpect(content().string(containsString("SHOW ROUTE")))
                .andExpect(content().string(containsString("2D VIEW")))
                .andExpect(content().string(containsString("maplibre-gl.js")))
                .andExpect(content().string(containsString("tracking-map.js")))
                .andExpect(content().string(not(containsString(
                        "GPS controls will activate when coordinates are available."
                ))))
                .andExpect(content().string(not(containsString("Private Recipient"))))
                .andExpect(content().string(not(containsString("Alex"))));

        verify(shipmentClient).getShipmentById(15L);
    }

    @Test
    void showsNotFoundErrorForMissingShipment() throws Exception {
        when(shipmentClient.getShipmentById(99L))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND,
                        "Not Found",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        mockMvc.perform(get("/tracking").param("shipmentId", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Shipment not found. Check the ID and try again."
                )))
                .andExpect(content().string(containsString("data-we-modal-alert")))
                .andExpect(content().string(not(containsString("id=\"live-map\""))))
                .andExpect(content().string(not(containsString("class=\"tracking-card\""))))
                .andExpect(content().string(not(containsString("FOLLOW COURIER"))));
    }

    @Test
    void keepsPageAvailableWhenTrackingServiceIsUnavailable() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Shipment information is temporarily unavailable."
                )))
                .andExpect(content().string(containsString("data-we-modal-alert")))
                .andExpect(content().string(not(containsString("id=\"live-map\""))))
                .andExpect(content().string(not(containsString("class=\"tracking-card\""))))
                .andExpect(content().string(not(containsString("FOLLOW COURIER"))));
    }
}
