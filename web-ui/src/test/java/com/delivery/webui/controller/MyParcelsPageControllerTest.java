package com.delivery.webui.controller;

import com.delivery.webui.client.ShipmentReadClient;
import com.delivery.webui.client.dto.ClientShipmentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

@SpringBootTest(properties = "shipment.creation.sender-id=42")
@AutoConfigureMockMvc
class MyParcelsPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentReadClient shipmentClient;

    @Test
    void showsRealParcelsWithExistingDesignAndLinks() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(List.of(
                shipment(515, 42, "WAITING_FOR_COURIER"),
                shipment(516, 42, "IN_TRANSIT"),
                shipment(517, 42, "DELIVERED")));

        mockMvc.perform(get("/my-parcels"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/my-parcels"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("SHIPMENT #515")))
                .andExpect(content().string(containsString("SHIPMENT #516")))
                .andExpect(content().string(containsString("SHIPMENT #517")))
                .andExpect(content().string(containsString("WAITING FOR COURIER")))
                .andExpect(content().string(containsString("IN TRANSIT")))
                .andExpect(content().string(containsString("DELIVERED")))
                .andExpect(content().string(containsString("Not assigned")))
                .andExpect(content().string(containsString("Test Courier Kyiv")))
                .andExpect(content().string(containsString("Kyiv, Kontraktova Square 2")))
                .andExpect(content().string(containsString("href=\"/my-parcels/515\"")))
                .andExpect(content().string(containsString("href=\"/tracking?shipmentId=515&amp;previewRole=CLIENT\"")))
                .andExpect(content().string(containsString("web-button__web--left")))
                .andExpect(content().string(containsString("web-button__web--right")))
                .andExpect(content().string(containsString("data-profile-menu")))
                .andExpect(content().string(not(containsString("SHIPMENT #15"))))
                .andExpect(content().string(not(containsString("Track and manage your deliveries."))));

        verify(shipmentClient).getMyShipments(42, 0, 20);
    }

    @Test
    void nextPageLoadShowsNewShipmentAndChangedStatus() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(
                List.of(),
                List.of(shipment(515, 42, "WAITING_FOR_COURIER")),
                List.of(shipment(515, 42, "ASSIGNED")),
                List.of(shipment(515, 42, "IN_TRANSIT")),
                List.of(shipment(515, 42, "DELIVERED")));

        mockMvc.perform(get("/my-parcels")).andExpect(content().string(containsString("YOU HAVE NO PARCELS YET.")));
        for (String expectedStatus : List.of("WAITING FOR COURIER", "ASSIGNED", "IN TRANSIT", "DELIVERED")) {
            mockMvc.perform(get("/my-parcels"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("SHIPMENT #515")))
                    .andExpect(content().string(containsString(expectedStatus)));
        }
    }

    @Test
    void emptyListHasWorkingCreateShipmentLink() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/my-parcels"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("YOU HAVE NO PARCELS YET.")))
                .andExpect(content().string(containsString("href=\"/ship\"")))
                .andExpect(content().string(not(containsString("SHIPMENT #"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "empty", "loading", "error"})
    void oldPreviewParametersCannotReplaceBackendData(String state) throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(List.of(shipment(515, 42, "ASSIGNED")));

        mockMvc.perform(get("/my-parcels").param("state", state)
                        .param("senderId", "99").param("userId", "99").header("X-User-Id", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SHIPMENT #515")));
        verify(shipmentClient).getMyShipments(42, 0, 20);
    }

    @Test
    void usesBoundedPagesAndProvidesNavigation() throws Exception {
        when(shipmentClient.getMyShipments(42, 1, 20)).thenReturn(
                LongStream.range(500, 520).mapToObj(id -> shipment(id, 42, "ASSIGNED")).toList());

        mockMvc.perform(get("/my-parcels").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAGE 2")))
                .andExpect(content().string(containsString("href=\"/my-parcels?page=0\"")))
                .andExpect(content().string(containsString("href=\"/my-parcels?page=2\"")));
        verify(shipmentClient).getMyShipments(42, 1, 20);
    }

    @Test
    void emptyLaterPageDoesNotClaimClientHasNoShipments() throws Exception {
        when(shipmentClient.getMyShipments(42, 1, 20)).thenReturn(List.of());

        mockMvc.perform(get("/my-parcels").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NO PARCELS ON THIS PAGE.")))
                .andExpect(content().string(not(containsString("YOU HAVE NO PARCELS YET."))))
                .andExpect(content().string(containsString("PREVIOUS")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc"})
    void invalidPageDoesNotCallBackend(String page) throws Exception {
        mockMvc.perform(get("/my-parcels").param("page", page)).andExpect(status().isBadRequest());
        verifyNoInteractions(shipmentClient);
    }

    @Test
    void backendFailureShowsErrorInsteadOfEmptyList() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenThrow(new ResourceAccessException("internal connection failure"));

        mockMvc.perform(get("/my-parcels").accept(MediaType.TEXT_HTML))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("pages/my-parcels"))
                .andExpect(content().string(containsString("PARCELS ARE TEMPORARILY UNAVAILABLE.")))
                .andExpect(content().string(containsString("TRY AGAIN")))
                .andExpect(content().string(not(containsString("YOU HAVE NO PARCELS YET."))))
                .andExpect(content().string(not(containsString("internal connection failure"))));
    }

    @Test
    void invalidOrForeignResponseIsNotDisplayed() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(
                List.of(shipment(999, 99, "IN_TRANSIT")));

        mockMvc.perform(get("/my-parcels"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("SHIPMENT #999"))))
                .andExpect(content().string(not(containsString("Kontraktova"))));
    }

    @Test
    void missingResponseIsUnavailableInsteadOfEmpty() throws Exception {
        when(shipmentClient.getMyShipments(42, 0, 20)).thenReturn(null);

        mockMvc.perform(get("/my-parcels")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("PARCELS ARE TEMPORARILY UNAVAILABLE.")));
    }

    @Test
    void detailsShowRealDataAndCorrectUnits() throws Exception {
        when(shipmentClient.getShipment(515)).thenReturn(shipment(515, 42, "IN_TRANSIT"));

        mockMvc.perform(get("/my-parcels/515"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/shipment-details"))
                .andExpect(model().attribute("activePage", "my-parcels"))
                .andExpect(content().string(containsString("<a class=\"back\" href=\"/my-parcels\">")))
                .andExpect(content().string(containsString("BACK TO MY PARCELS")))
                .andExpect(content().string(containsString("SHIPMENT #515")))
                .andExpect(content().string(containsString("Kyiv, Illinska 2")))
                .andExpect(content().string(containsString("RECIPIENT PHONE")))
                .andExpect(content().string(containsString("19 Nov 2026, 12:34")))
                .andExpect(content().string(containsString("20 m")))
                .andExpect(content().string(containsString("1 kg")))
                .andExpect(content().string(containsString("20 cm")))
                .andExpect(content().string(containsString("60.03 UAH")))
                .andExpect(content().string(containsString("COURIER PHONE")))
                .andExpect(content().string(containsString("Not available")))
                .andExpect(content().string(containsString("Test Courier Kyiv")))
                .andExpect(content().string(not(containsString("SHIPMENT DETAILS"))));
    }

    @Test
    void detailsOpenedFromNotificationsKeepNotificationNavigation() throws Exception {
        when(shipmentClient.getShipment(515)).thenReturn(shipment(515, 42, "DELIVERED"));

        mockMvc.perform(get("/my-parcels/515").param("from", "notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/shipment-details"))
                .andExpect(model().attribute("activePage", "notifications"))
                .andExpect(content().string(containsString("BACK TO NOTIFICATIONS")))
                .andExpect(content().string(containsString("<a class=\"back\" href=\"/notifications\">")))
                .andExpect(content().string(containsString("site-header__desktop-only site-header__notifications active")))
                .andExpect(content().string(not(containsString("BACK TO MY PARCELS"))))
                .andExpect(content().string(containsString("SHIPMENT #515")));
        verify(shipmentClient).getShipment(515);
    }

    @Test
    void detailsReturnNotFoundForMissingOrForeignShipment() throws Exception {
        when(shipmentClient.getShipment(99999)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));
        when(shipmentClient.getShipment(999)).thenReturn(shipment(999, 99, "ASSIGNED"));

        mockMvc.perform(get("/my-parcels/99999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/my-parcels/999")).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Test Courier Kyiv"))));
    }

    @Test
    void detailsBackendFailureUsesSameErrorPage() throws Exception {
        when(shipmentClient.getShipment(515)).thenThrow(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error", HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(get("/my-parcels/515"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("PARCELS ARE TEMPORARILY UNAVAILABLE.")));
    }

    @Test
    void rejectsInvalidIdAndNonLocalAccessBeforeReadingData() throws Exception {
        mockMvc.perform(get("/my-parcels/0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/my-parcels").with(request -> {
            request.setRemoteAddr("192.0.2.10");
            return request;
        })).andExpect(status().isForbidden());
        mockMvc.perform(get("/my-parcels/515").with(request -> {
            request.setRemoteAddr("192.0.2.10");
            return request;
        })).andExpect(status().isForbidden());
        verifyNoInteractions(shipmentClient);
    }

    private static ClientShipmentResponse shipment(long id, long userId, String status) {
        boolean assigned = !"WAITING_FOR_COURIER".equals(status);
        return new ClientShipmentResponse(id, userId, status, "Kyiv, Illinska 2", "Kyiv, Kontraktova Square 2",
                "Test Recipient", "+380501234569", LocalDateTime.of(2026, 11, 19, 12, 34),
                new BigDecimal("0.02"), BigDecimal.ONE, new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("10"), new BigDecimal("60.03"), assigned ? 7001L : null,
                assigned ? "Test Courier Kyiv" : null);
    }
}
