package com.delivery.shipment.geo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeoapifyRoutingClientTest {

    private static final String KYIV = "UA80000000000093317";
    private static final String TEST_KEY = "mock-geoapify-key";
    private static final Coordinates PICKUP = new Coordinates(50.4501, 30.5234);
    private static final Coordinates DELIVERY = new Coordinates(50.451, 30.524);

    private MockRestServiceServer server;
    private RestClient restClient;
    private GeoapifyRoutingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new GeoapifyRoutingClient(restClient, TEST_KEY);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void geocodesTheFullAddressAndKeepsTheKeyOutOfTheUrl() {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/geocode/search?")))
                .andExpect(header("x-api-key", TEST_KEY))
                .andExpect(request -> {
                    assertThat(request.getURI().getQuery()).contains(
                            "text=Київ, A & B 1", "filter=countrycode:ua", "format=json", "limit=1");
                    assertThat(request.getURI().getRawQuery()).contains("%26").doesNotContain(TEST_KEY, "apiKey=");
                })
                .andRespond(withSuccess("""
                        {"results":[{"lat":50.4501,"lon":30.5234,"result_type":"building"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.geocode(KYIV, "Київ, A & B 1")).isEqualTo(PICKUP);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"results\":[]}",
            "{\"results\":[{\"lat\":50.45,\"lon\":30.52,\"result_type\":\"city\"}]}"
    })
    void rejectsMissingAddressesAndCityOnlyMatches(String body) {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/geocode/search?")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.geocode(KYIV, "Київ, невідома вулиця, 123"))
                .isInstanceOf(AddressNotGeocodableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"results\":[{\"result_type\":\"building\"}]}",
            "{\"results\":[{\"lat\":95,\"lon\":30,\"result_type\":\"building\"}]}",
            "not json"
    })
    void rejectsInvalidGeocodingResponses(String body) {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/geocode/search?")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.geocode(KYIV, "Київ, Хрещатик, 1"))
                .isInstanceOf(GeoapifyUnavailableException.class);
    }

    @Test
    void requestsDrivingDistanceWithLatitudeFirstAndConvertsMetersToKilometers() {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/routing?")))
                .andExpect(header("x-api-key", TEST_KEY))
                .andExpect(request -> {
                    assertThat(request.getURI().getQuery()).contains(
                            "waypoints=50.4501,30.5234|50.451,30.524", "mode=drive", "units=metric", "format=geojson");
                    assertThat(request.getURI().getRawQuery()).doesNotContain(TEST_KEY, "apiKey=");
                })
                .andRespond(withSuccess("""
                        {"features":[{"properties":{"distance":12345,"distance_units":"meters"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.routeDistanceKm(PICKUP, DELIVERY)).isEqualTo(12.345);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"features\":[]}",
            "{\"features\":[{\"properties\":{\"distance_units\":\"meters\"}}]}",
            "{\"features\":[{\"properties\":{\"distance\":-1,\"distance_units\":\"meters\"}}]}",
            "{\"features\":[{\"properties\":{\"distance\":12,\"distance_units\":\"miles\"}}]}"
    })
    void doesNotInventDistanceForInvalidOrMissingRoutes(String body) {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/routing?")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.routeDistanceKm(PICKUP, DELIVERY))
                .isInstanceOf(GeoapifyUnavailableException.class);
    }

    @Test
    void doesNotExposeTheProviderResponseOrException() {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/geocode/search?")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Invalid key: " + TEST_KEY + "\"}"));

        assertThatThrownBy(() -> client.geocode(KYIV, "Київ, Хрещатик, 1"))
                .isInstanceOf(GeoapifyUnavailableException.class)
                .hasMessageNotContaining(TEST_KEY)
                .hasNoCause();
    }

    @Test
    void reportsTimeoutWithoutFallbackDistance() {
        server.expect(requestTo(startsWith("https://api.geoapify.com/v1/routing?")))
                .andRespond(withException(new SocketTimeoutException("simulated timeout")));

        assertThatThrownBy(() -> client.routeDistanceKm(PICKUP, DELIVERY))
                .isInstanceOf(GeoapifyUnavailableException.class)
                .hasNoCause();
    }

    @Test
    void reportsMissingKeyBeforeMakingARequest() {
        GeoapifyRoutingClient unconfigured = new GeoapifyRoutingClient(restClient, "");

        assertThatThrownBy(() -> unconfigured.geocode(KYIV, "Київ, Хрещатик, 1"))
                .isInstanceOf(GeoapifyUnavailableException.class);
    }
}
