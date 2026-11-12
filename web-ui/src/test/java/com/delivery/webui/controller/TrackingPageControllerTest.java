package com.delivery.webui.controller;

import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.DispatchCourierResponse;
import com.delivery.webui.client.dto.ShipmentDetailsResponse;
import com.delivery.webui.client.dto.TrackingLocationResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private TrackingLocationClient locationClient;

    @MockitoBean
    private DispatchCourierClient courierClient;

    @Test
    void contextLoads() {
    }

    @Test
    void showsGuestHeaderByDefault() throws Exception {
        mockMvc.perform(get("/tracking"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/tracking\"")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("href=\"/register\"")))
                .andExpect(content().string(containsString("SIGN UP")))
                .andExpect(content().string(not(containsString("data-profile-menu"))));
    }

    @Test
    void showsAuthenticatedHeaderPreview() throws Exception {
        mockMvc.perform(get("/tracking").param("authenticated", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SHIP")))
                .andExpect(content().string(containsString("MY PARCELS")))
                .andExpect(content().string(containsString("PROFILE")))
                .andExpect(content().string(containsString("MY PROFILE")))
                .andExpect(content().string(containsString("LOG OUT")))
                .andExpect(content().string(containsString("data-profile-menu")))
                .andExpect(content().string(containsString("aria-expanded=\"false\"")))
                .andExpect(content().string(not(containsString("href=\"/login\""))))
                .andExpect(content().string(not(containsString("href=\"/register\""))));
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
                .andExpect(content().string(not(containsString("tracking-live.js"))))
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
        verifyNoInteractions(locationClient);
        verifyNoInteractions(courierClient);
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
        verifyNoInteractions(locationClient);
        verifyNoInteractions(courierClient);
    }

    @Test
    void showsRealShipmentStatusWithoutRecipientData() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        "Private Recipient",
                        "IN_TRANSIT"
                ));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());
        when(shipmentClient.getShipmentDetails(15L))
                .thenReturn(new ShipmentDetailsResponse(
                        15L,
                        "Kyiv, Delivery Street 2"
                ));
        when(courierClient.getShipmentCourier(15L))
                .thenReturn(new DispatchCourierResponse(
                        15L,
                        true,
                        "Andrii Kovalenko"
                ));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"live-map\"")))
                .andExpect(content().string(containsString("class=\"tracking-card\"")))
                .andExpect(result -> assertThat(Pattern.compile(
                                "class=\"tracking-card__field(?:\\s|\")"
                        ).matcher(result.getResponse().getContentAsString())
                        .results()
                        .count()).isEqualTo(4))
                .andExpect(content().string(containsString("SHIPMENT")))
                .andExpect(content().string(containsString("#15")))
                .andExpect(content().string(containsString("STATUS")))
                .andExpect(content().string(containsString("YOUR COURIER IS ON THE WAY")))
                .andExpect(content().string(containsString("DESTINATION")))
                .andExpect(content().string(containsString("Kyiv, Delivery Street 2")))
                .andExpect(content().string(containsString("COURIER")))
                .andExpect(content().string(containsString("Andrii Kovalenko")))
                .andExpect(content().string(containsString("WAITING FOR COURIER LOCATION")))
                .andExpect(content().string(containsString("FOLLOW COURIER")))
                .andExpect(content().string(containsString("SHOW ROUTE")))
                .andExpect(content().string(containsString("2D VIEW")))
                .andExpect(content().string(containsString("maplibre-gl.js")))
                .andExpect(content().string(containsString("tracking-map.js")))
                .andExpect(content().string(not(containsString("tracking-live.js"))))
                .andExpect(content().string(not(containsString(
                        "id=\"tracking-stream-status\""
                ))))
                .andExpect(content().string(not(containsString(
                        "GPS controls will activate when coordinates are available."
                ))))
                .andExpect(content().string(not(containsString("Private Recipient"))))
                .andExpect(content().string(not(containsString("Alex"))))
                .andExpect(content().string(not(containsString("SPECIAL DELIVERY"))))
                .andExpect(content().string(not(containsString("ETA"))))
                .andExpect(content().string(not(containsString("SHIPMENT PRIVACY"))))
                .andExpect(content().string(not(containsString(
                        "Recipient information is not displayed."
                ))));

        verify(shipmentClient).getShipmentById(15L);
        verify(shipmentClient).getShipmentDetails(15L);
        verify(courierClient).getShipmentCourier(15L);
        verify(locationClient).getShipmentLocations(15, 0, 100);
    }

    @Test
    void showsNotAssignedOnlyWhenDispatchConfirmsIt() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        null,
                        "WAITING_FOR_COURIER"
                ));
        when(courierClient.getShipmentCourier(15L))
                .thenReturn(new DispatchCourierResponse(
                        15L,
                        false,
                        null
                ));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not assigned")))
                .andExpect(content().string(containsString(
                        "WAITING FOR COURIER"
                )));
    }

    @Test
    void keepsMapAndStatusWhenDispatchIsUnavailable() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        null,
                        "IN_TRANSIT"
                ));
        when(courierClient.getShipmentCourier(15L))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of(location(
                        1,
                        "50.450100",
                        "30.523400"
                )));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not available")))
                .andExpect(content().string(containsString(
                        "YOUR COURIER IS ON THE WAY"
                )))
                .andExpect(content().string(containsString(
                        "COURIER LOCATION AVAILABLE"
                )));
    }

    @Test
    void serializesRealCoordinatesForTrackingMap() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        null,
                        "IN_TRANSIT"
                ));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of(
                        location(1, "50.450100", "30.523400"),
                        location(2, "50.451000", "30.525000")
                ));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"locationId\":1,\"shipmentId\":15,\"latitude\":50.450100,\"longitude\":30.523400"
                )))
                .andExpect(content().string(containsString(
                        "\"locationId\":2,\"shipmentId\":15,\"latitude\":50.451000,\"longitude\":30.525000"
                )))
                .andExpect(content().string(containsString(
                        "locationHistoryComplete: true"
                )))
                .andExpect(content().string(containsString(
                        "COURIER LOCATION AVAILABLE"
                )));
    }

    @Test
    void keepsShipmentVisibleWhenLocationApiIsUnavailable() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(
                        15L,
                        null,
                        "IN_TRANSIT"
                ));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/tracking").param("shipmentId", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"live-map\"")))
                .andExpect(content().string(containsString(
                        "YOUR COURIER IS ON THE WAY"
                )))
                .andExpect(content().string(containsString(
                        "LOCATION DATA TEMPORARILY UNAVAILABLE"
                )));
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

        verify(locationClient, never())
                .getShipmentLocations(99, 0, 100);
        verifyNoInteractions(courierClient);
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

        verifyNoInteractions(locationClient);
        verifyNoInteractions(courierClient);
    }

    private TrackingLocationResponse location(
            long locationId,
            String latitude,
            String longitude
    ) {
        return new TrackingLocationResponse(
                locationId,
                15L,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                LocalDateTime.of(2026, 11, 12, 9, 30)
        );
    }
}
