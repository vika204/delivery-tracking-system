package com.delivery.shipment.geo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

@Component
public class GeoapifyRoutingClient implements RoutingClient {

    private static final Set<String> ADDRESS_TYPES = Set.of("building", "street", "amenity");

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public GeoapifyRoutingClient(RestClient.Builder builder, @Value("${geoapify.api-key}") String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl("https://api.geoapify.com")
                .requestFactory(requestFactory).build();
        this.apiKey = apiKey.trim();
    }

    GeoapifyRoutingClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey.trim();
    }

    @Override
    public Coordinates geocode(String cityCode, String addressLine) {
        if (cityCode == null || !cityCode.matches("UA\\d{17}")) {
            throw new AddressNotGeocodableException("Select a Ukrainian settlement from the KATOTTG list.");
        }
        if (addressLine == null || addressLine.isBlank()) {
            throw new AddressNotGeocodableException("Enter the settlement, street and house number.");
        }
        requireApiKey();

        JsonNode response;
        try {
            response = restClient.get().uri(builder -> builder.path("/v1/geocode/search")
                            .queryParam("text", "{address}")
                            .queryParam("filter", "countrycode:ua")
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build(addressLine.trim()))
                    .header("x-api-key", apiKey)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            // Provider responses and request exceptions may contain credentials.
            throw new GeoapifyUnavailableException();
        }

        if (response == null || !response.path("results").isArray()) {
            throw new GeoapifyUnavailableException();
        }
        JsonNode results = response.path("results");
        if (results.isEmpty() || !ADDRESS_TYPES.contains(results.path(0).path("result_type").asText(""))) {
            throw new AddressNotGeocodableException("Address not found. Check the settlement, street and house number.");
        }

        JsonNode result = results.path(0);
        if (!result.path("lat").isNumber() || !result.path("lon").isNumber()) {
            throw new GeoapifyUnavailableException();
        }
        Coordinates coordinates = new Coordinates(result.path("lat").doubleValue(), result.path("lon").doubleValue());
        if (!validCoordinates(coordinates)) {
            throw new GeoapifyUnavailableException();
        }
        return coordinates;
    }

    @Override
    public double routeDistanceKm(Coordinates from, Coordinates to) {
        if (!validCoordinates(from) || !validCoordinates(to)) {
            throw new AddressNotGeocodableException("Valid pickup and delivery coordinates are required.");
        }
        requireApiKey();
        String waypoints = from.latitude() + "," + from.longitude() + "|" + to.latitude() + "," + to.longitude();

        JsonNode response;
        try {
            response = restClient.get().uri(builder -> builder.path("/v1/routing")
                            .queryParam("waypoints", "{waypoints}")
                            .queryParam("mode", "drive")
                            .queryParam("units", "metric")
                            .queryParam("format", "geojson")
                            .build(waypoints))
                    .header("x-api-key", apiKey)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new GeoapifyUnavailableException();
        }

        if (response == null) {
            throw new GeoapifyUnavailableException();
        }
        JsonNode properties = response.path("features").path(0).path("properties");
        JsonNode distance = properties.path("distance");
        if (!distance.isNumber()
                || !Double.isFinite(distance.doubleValue())
                || distance.doubleValue() < 0
                || !"meters".equalsIgnoreCase(properties.path("distance_units").asText(""))) {
            throw new GeoapifyUnavailableException();
        }
        return distance.doubleValue() / 1000.0;
    }

    private void requireApiKey() {
        if (apiKey.isBlank()) {
            throw new GeoapifyUnavailableException();
        }
    }

    private static boolean validCoordinates(Coordinates coordinates) {
        return coordinates != null
                && Double.isFinite(coordinates.latitude())
                && coordinates.latitude() >= -90 && coordinates.latitude() <= 90
                && Double.isFinite(coordinates.longitude())
                && coordinates.longitude() >= -180 && coordinates.longitude() <= 180;
    }
}
