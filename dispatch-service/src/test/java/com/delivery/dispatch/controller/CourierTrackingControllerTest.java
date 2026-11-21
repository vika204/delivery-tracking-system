package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.LocationPointRequest;
import com.delivery.dispatch.exception.DispatchExceptionHandler;
import com.delivery.dispatch.service.CourierTrackingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourierTrackingControllerTest {

    private static final String POINT = """
            {"latitude":50.45,"longitude":30.52,"recordedAt":"2026-11-19T10:00:00Z"}
            """;
    private final CourierTrackingService service = mock(CourierTrackingService.class);
    private final MockMvc mvc = standaloneSetup(new CourierTrackingController(service))
            .setControllerAdvice(new DispatchExceptionHandler()).build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"points\":[]}", "{\"points\":[null]}",
            "{\"points\":[{\"latitude\":91,\"longitude\":30,\"recordedAt\":\"2026-11-19T10:00:00Z\"}]}",
            "{\"points\":[{\"latitude\":50,\"longitude\":-181,\"recordedAt\":\"2026-11-19T10:00:00Z\"}]}",
            "{\"points\":[{\"longitude\":30,\"recordedAt\":\"2026-11-19T10:00:00Z\"}]}",
            "{\"points\":[{\"latitude\":50,\"longitude\":30}]}"
    })
    void rejectsInvalidNestedPointsBeforeCallingGrpc(String body) throws Exception {
        for (String endpoint : List.of("route", "live-locations")) {
            mvc.perform(post("/dispatch/shipments/15/" + endpoint)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:delivery:problem:validation-failed"))
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMoreThanOneThousandPoints() throws Exception {
        String body = "{\"points\":[" + String.join(",", Collections.nCopies(1001, POINT)) + "]}";
        mvc.perform(post("/dispatch/shipments/15/route").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void passesValidPointsToBothGrpcOperations() throws Exception {
        String body = "{\"points\":[" + POINT + "]}";
        mvc.perform(post("/dispatch/shipments/15/route").header("Idempotency-Key", "route-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/dispatch/shipments/15/live-locations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        var points = List.of(new LocationPointRequest(50.45, 30.52, Instant.parse("2026-11-19T10:00:00Z")));
        verify(service).uploadRoute(15, "route-1", points);
        verify(service).streamLiveLocations(15, points);
    }
}
