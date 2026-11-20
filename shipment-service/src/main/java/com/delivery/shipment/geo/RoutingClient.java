package com.delivery.shipment.geo;

public interface RoutingClient {

    Coordinates geocode(String cityCode, String addressLine);

    double routeDistanceKm(Coordinates from, Coordinates to);
}
