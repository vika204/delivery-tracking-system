package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.AssignmentResponse;
import com.delivery.dispatch.dto.RegisterCourierRequest;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.exception.DispatchExceptionHandler;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourierControllerTest {

    private final CourierService courierService = mock(CourierService.class);
    private final DispatchService dispatchService = mock(DispatchService.class);
    private final MockMvc mockMvc = standaloneSetup(new CourierController(courierService, dispatchService))
            .setControllerAdvice(new DispatchExceptionHandler())
            .build();

    @Test
    void preservesDisplayNameWhenCreatingCourier() throws Exception {
        when(courierService.register(any(RegisterCourierRequest.class))).thenAnswer(invocation -> {
            RegisterCourierRequest request = invocation.getArgument(0);
            return Courier.builder()
                    .userId(request.userId())
                    .displayName(request.displayName())
                    .workZone(request.workZone())
                    .workZoneCode(request.workZoneCode())
                    .maxPackageWeight(request.maxPackageWeight())
                    .isAvailable(true)
                    .build();
        });

        mockMvc.perform(post("/couriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 10,
                                  "displayName": "Andrii Kovalenko",
                                  "workZone": "Kyiv",
                                  "workZoneCode": "UA80000000000093317",
                                  "maxPackageWeight": 15.5,
                                  "available": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.displayName").value("Andrii Kovalenko"))
                .andExpect(jsonPath("$.maxPackageWeight").value(new BigDecimal("15.5")));
    }

    @Test
    void acceptReturnsTheCreatedAssignment() throws Exception {
        Assignment assignment = Assignment.builder().assignmentId(3L).shipmentId(15L).status(AssignmentStatus.ACTIVE).build();
        when(dispatchService.acceptOffer(10, 55)).thenReturn(assignment);
        when(courierService.describe(assignment)).thenReturn(new AssignmentResponse(
                3, 15, AssignmentStatus.ACTIVE, false, null, null, null, null,
                "Kyiv, A", "Kyiv, B", null, null, null));

        mockMvc.perform(post("/couriers/10/offers/55/accept").header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(3))
                .andExpect(jsonPath("$.pickupAddress").value("Kyiv, A"));

        verify(courierService, never()).assignments(anyLong(), anyBoolean());
    }

    @Test
    void mapsDatabaseConstraintViolationToConflict() throws Exception {
        when(dispatchService.acceptOffer(10, 55)).thenThrow(new DataIntegrityViolationException("uq_assignment_active_courier"));

        mockMvc.perform(post("/couriers/10/offers/55/accept").header("X-User-Id", "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The action conflicts with the current state, please retry"));
    }

    @Test
    void rejectsCourierActingForAnotherCourier() throws Exception {
        mockMvc.perform(put("/couriers/10/availability")
                        .header("X-User-Id", "11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": true}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courierService);
    }

    @Test
    void rejectsInvalidWorkZoneCode() throws Exception {
        mockMvc.perform(post("/couriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 10, "workZone": "Kyiv", "workZoneCode": "Kyiv", "maxPackageWeight": 15}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courierService);
    }
}
