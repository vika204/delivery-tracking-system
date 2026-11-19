package com.delivery.shipment.geo;

/**
 * Anti-Corruption Layer до Maps/Routing API.
 * <p>
 * Shipment і Pricing працюють лише з внутрішніми типами ({@link Coordinates}, км),
 * а формат Geoapify ховається в {@link GeoapifyRoutingClient}.
 */
public interface RoutingClient {

    /**
     * Геокодує адресу.
     *
     * @param cityCode    код населеного пункту за KATOTTG (UI передає його з автодоповнення)
     * @param addressLine повна адреса з назвою населеного пункту, вулицею й номером будинку
     * @throws AddressNotGeocodableException якщо адресу неможливо визначити
     */
    Coordinates geocode(String cityCode, String addressLine);

    /** Відстань маршруту між точками, км. */
    double routeDistanceKm(Coordinates from, Coordinates to);
}
