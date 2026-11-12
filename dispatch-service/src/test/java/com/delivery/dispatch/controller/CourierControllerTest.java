package com.delivery.dispatch.controller;

import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.metrics.CourierMetrics;
import com.delivery.dispatch.repository.CourierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourierControllerTest {

    private final CourierRepository repository =
            mock(CourierRepository.class);
    private final CourierMetrics metrics = mock(CourierMetrics.class);
    private final MockMvc mockMvc = standaloneSetup(
            new CourierController(repository, metrics)
    ).build();

    @Test
    void preservesDisplayNameWhenCreatingCourier() throws Exception {
        when(repository.save(any(Courier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/couriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 10,
                                  "displayName": "Andrii Kovalenko",
                                  "workZone": "Kyiv",
                                  "maxPackageWeight": 15.5,
                                  "isAvailable": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.displayName")
                        .value("Andrii Kovalenko"));

        verify(metrics).courierRegistered(any());
    }
}
