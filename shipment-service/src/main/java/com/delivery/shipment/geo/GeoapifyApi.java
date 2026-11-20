package com.delivery.shipment.geo;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import tools.jackson.databind.JsonNode;

@HttpExchange("/v1")
public interface GeoapifyApi {

    @GetExchange("/geocode/search?filter=countrycode:ua&format=json&limit=1")
    JsonNode geocode(@RequestParam("text") String address, @RequestHeader("x-api-key") String apiKey);

    @GetExchange("/routing?mode=drive&units=metric&format=geojson")
    JsonNode route(@RequestParam("waypoints") String waypoints, @RequestHeader("x-api-key") String apiKey);
}
