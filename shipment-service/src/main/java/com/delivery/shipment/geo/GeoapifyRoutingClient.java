package com.delivery.shipment.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.Set;

@Component
public class GeoapifyRoutingClient implements RoutingClient {

    private static final Set<String> ADDRESS_TYPES = Set.of("building", "street", "amenity");

    private final GeoapifyApi geoapifyApi;
    private final String apiKey;

    public GeoapifyRoutingClient(GeoapifyApi geoapifyApi, @Value("${geoapify.api-key}") String apiKey) {
        this.geoapifyApi = geoapifyApi;
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
            response = geoapifyApi.geocode(addressLine.trim(), apiKey);
        } catch (RestClientException ex) {
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
            response = geoapifyApi.route(waypoints, apiKey);
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
