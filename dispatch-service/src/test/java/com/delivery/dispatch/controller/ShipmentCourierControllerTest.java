package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.ShipmentCourierResponse;
import com.delivery.dispatch.service.ShipmentCourierService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ShipmentCourierControllerTest {

    private final ShipmentCourierService service =
            mock(ShipmentCourierService.class);
    private final MockMvc mockMvc = standaloneSetup(
            new ShipmentCourierController(service)
    ).build();

    @Test
    void returnsPublicCourierAssignmentData() throws Exception {
        when(service.getCurrentCourier(15L))
                .thenReturn(new ShipmentCourierResponse(
                        15L,
                        true,
                        "Andrii Kovalenko"
                ));

        mockMvc.perform(get("/dispatch/shipments/15/courier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.courierName")
                        .value("Andrii Kovalenko"))
                .andExpect(jsonPath("$.courierId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());

        verify(service).getCurrentCourier(15L);
    }

    @Test
    void rejectsNonPositiveShipmentId() throws Exception {
        mockMvc.perform(get("/dispatch/shipments/0/courier"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
