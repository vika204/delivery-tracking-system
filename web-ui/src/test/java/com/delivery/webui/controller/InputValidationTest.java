package com.delivery.webui.controller;

import com.delivery.webui.client.AuthUserClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.UserProfileClient;
import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.exception.ApiExceptionHandler;
import com.delivery.webui.service.CourierDashboardService;
import com.delivery.webui.service.CourierDeliveryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InputValidationTest {

    private static final String CLIENT = """
            {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com",
             "phone":"+380671234567","password":"test-password"}
            """;
    private static final String COURIER = """
            {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com",
             "phone":"+380671234567","password":"test-password",
             "workZone":"Kyiv","workZoneCode":"UA80000000000093317","maxWeight":20}
            """;
    private static final String QUOTE = """
            {"pickupAddress":"Kyiv, A, 1","pickupCityCode":"UA80000000000093317",
             "deliveryAddress":"Kyiv, B, 2","deliveryCityCode":"UA80000000000093317","weight":1}
            """;
    private static final String SHIPMENT = """
            {"quoteId":"3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f","senderName":"Olena",
             "senderPhone":"+380501234567","recipientName":"Marta","recipientPhone":"+380671234567",
             "length":30,"width":20,"height":15}
            """;

    private final AuthUserClient users = mock(AuthUserClient.class);
    private final UserProfileClient profiles = mock(UserProfileClient.class);
    private final DispatchCourierDashboardClient couriers = mock(DispatchCourierDashboardClient.class);
    private final ShipmentCreationClient shipments = mock(ShipmentCreationClient.class);
    private final CourierDeliveryService delivery = mock(CourierDeliveryService.class);
    private final CourierDashboardService dashboard = mock(CourierDashboardService.class);
    private final MockMvc mvc = standaloneSetup(
            new AuthApiController(users, couriers),
            new ProfileApiController(profiles, couriers, new ShipmentCreationProperties(42), new CourierDashboardProperties(7)),
            new ShipmentCreationApiController(shipments, new ShipmentCreationProperties(42)),
            new CourierDeliveryApiController(delivery, new CourierDashboardProperties(7)),
            new CourierDashboardApiController(dashboard, new CourierDashboardProperties(7)))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputBeforeCallingAnyBackend(String method, String path, String body, String field) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Idempotency-Key", "validation-test")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));
        verifyNoInteractions(users, profiles, couriers, shipments, delivery, dashboard);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("POST", "/api/auth/register/client", CLIENT.replace("Olena", " "), "firstName"),
                Arguments.of("POST", "/api/auth/register/client", CLIENT.replace("Kovalenko", "a".repeat(71)), "lastName"),
                Arguments.of("POST", "/api/auth/register/client", CLIENT.replace("olena@example.com", "wrong-email"), "email"),
                Arguments.of("POST", "/api/auth/register/client", CLIENT.replace("+380671234567", "invalid-phone"), "phone"),
                Arguments.of("POST", "/api/auth/register/client", CLIENT.replace("test-password", "short"), "password"),
                Arguments.of("POST", "/api/auth/register/courier", COURIER.replace("Kyiv", " "), "workZone"),
                Arguments.of("POST", "/api/auth/register/courier", COURIER.replace("UA80000000000093317", "Kyiv"), "workZoneCode"),
                Arguments.of("POST", "/api/auth/register/courier", COURIER.replace(":20", ":0"), "maxWeight"),
                Arguments.of("POST", "/api/auth/login", "{\"email\":\"bad\",\"password\":\"test-password\"}", "email"),
                Arguments.of("POST", "/api/auth/login", "{\"email\":\"olena@example.com\",\"password\":\" \"}", "password"),
                Arguments.of("PUT", "/api/profile", CLIENT.replace("olena@example.com", "bad"), "email"),
                Arguments.of("PUT", "/api/courier/profile", COURIER.replace(":20", ":1001"), "maxWeight"),
                Arguments.of("PUT", "/api/courier/profile", CLIENT, "workZoneCode"),
                Arguments.of("POST", "/api/shipments/quotes", QUOTE.replace("Kyiv, A, 1", " "), "pickupAddress"),
                Arguments.of("POST", "/api/shipments/quotes", QUOTE.replace("UA80000000000093317", "wrong"), "pickupCityCode"),
                Arguments.of("POST", "/api/shipments/quotes", QUOTE.replace("\"weight\":1", "\"weight\":0"), "weight"),
                Arguments.of("POST", "/api/shipments/quotes", QUOTE.replace("\"weight\":1", "\"weight\":1001"), "weight"),
                Arguments.of("POST", "/api/shipments", SHIPMENT.replace("\"3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f\"", "null"), "quoteId"),
                Arguments.of("POST", "/api/shipments", SHIPMENT.replace("+380671234567", "abc"), "recipientPhone"),
                Arguments.of("POST", "/api/shipments", SHIPMENT.replace("\"length\":30", "\"length\":-1"), "length"),
                Arguments.of("POST", "/api/shipments", SHIPMENT.replace("\"height\":15", "\"height\":501"), "height"),
                Arguments.of("PUT", "/api/courier/dashboard/availability", "{}", "online"),
                Arguments.of("PUT", "/api/courier/dashboard/availability", "{\"online\":null}", "online"),
                Arguments.of("POST", "/api/courier/shipments/15/locations",
                        "{\"latitude\":91,\"longitude\":30,\"recordedAt\":\"2026-11-19T10:00:00Z\"}", "latitude"),
                Arguments.of("POST", "/api/courier/shipments/15/locations",
                        "{\"latitude\":50,\"longitude\":-181,\"recordedAt\":\"2026-11-19T10:00:00Z\"}", "longitude"),
                Arguments.of("POST", "/api/courier/shipments/15/locations", "{\"latitude\":50,\"longitude\":30}", "recordedAt"));
    }
}
