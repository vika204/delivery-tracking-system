package com.delivery.webui.controller;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.UserProfileClient;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.UpdateCourierProfileRequest;
import com.delivery.webui.client.dto.UserProfile;
import com.delivery.webui.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProfileApiControllerTest {

    private static final UserProfile USER = new UserProfile("Olena", "Kovalenko", "olena@example.com", "+380671234567");
    private static final String BODY = """
            {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com","phone":"+380671234567"}
            """;
    private static final String COURIER_BODY = """
            {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com","phone":"+380671234567",
             "workZone":"Kyiv","workZoneCode":"UA80000000000093317","maxWeight":20}
            """;
    private final UserProfileClient users = mock(UserProfileClient.class);
    private final DispatchCourierDashboardClient couriers = mock(DispatchCourierDashboardClient.class);
    private final MockMvc mvc = standaloneSetup(new ProfileApiController(users, couriers, new ShipmentCreationProperties(42), new CourierDashboardProperties(7)))
            .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor()).build();

    @Test
    void readsCurrentClientIgnoringCallerSuppliedIdentity() throws Exception {
        when(users.profile(42)).thenReturn(USER);
        mvc.perform(get("/api/profile").param("userId", "99").header("X-User-Id", "99"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.firstName").value("Olena"));
        verify(users).profile(42);
        verifyNoInteractions(couriers);
    }

    @Test
    void savesClientThroughExistingHttpServiceClient() throws Exception {
        when(users.updateProfile(42, USER)).thenReturn(USER);
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(USER.email()));
        verify(users).updateProfile(42, USER);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(couriers);
    }

    @Test
    void readsCourierAccountAndWorkingSettings() throws Exception {
        when(users.profile(7)).thenReturn(USER);
        when(couriers.profile(7)).thenReturn(courier());
        mvc.perform(get("/api/courier/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Olena"))
                .andExpect(jsonPath("$.workZone").value("Kyiv"))
                .andExpect(jsonPath("$.maxWeight").value(20));
        verify(users).profile(7);
        verify(couriers).profile(7);
    }

    @Test
    void savesCourierAccountAndDispatchFieldsWithoutAdditionalReads() throws Exception {
        when(users.updateProfile(7, USER)).thenReturn(USER);
        when(couriers.updateProfile(eq(7L), eq(7L), any())).thenReturn(courier());
        mvc.perform(put("/api/courier/profile").contentType(MediaType.APPLICATION_JSON).content(COURIER_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxWeight").value(20));
        verify(users).updateProfile(7, USER);
        verify(couriers).updateProfile(7, 7, new UpdateCourierProfileRequest(
                "Olena Kovalenko", USER.phone(), "Kyiv", "UA80000000000093317", new BigDecimal("20")));
        verifyNoMoreInteractions(users, couriers);
    }

    @Test
    void unavailableAccountDoesNotWriteCourierData() throws Exception {
        when(users.updateProfile(eq(7L), any())).thenThrow(new ResourceAccessException("private-host"));
        mvc.perform(put("/api/courier/profile").contentType(MediaType.APPLICATION_JSON).content(COURIER_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private-host"))));
        verifyNoInteractions(couriers);
    }

    @Test
    void dispatchWriteFailureDoesNotReportSuccessfulSave() throws Exception {
        when(users.updateProfile(7, USER)).thenReturn(USER);
        when(couriers.updateProfile(eq(7L), eq(7L), any())).thenThrow(new ResourceAccessException("private-dispatch"));
        mvc.perform(put("/api/courier/profile").contentType(MediaType.APPLICATION_JSON).content(COURIER_BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void profileEndpointsRemainLocalOnly() throws Exception {
        for (String path : new String[]{"/api/profile", "/api/courier/profile"}) {
            mvc.perform(get(path).with(request -> { request.setRemoteAddr("203.0.113.5"); return request; }))
                    .andExpect(status().isForbidden());
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .with(request -> { request.setRemoteAddr("203.0.113.5"); return request; }))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, couriers);
    }

    private static DispatchCourierProfile courier() {
        return new DispatchCourierProfile(7L, "Olena Kovalenko", USER.phone(), "Kyiv", "UA80000000000093317", new BigDecimal("20"));
    }
}
