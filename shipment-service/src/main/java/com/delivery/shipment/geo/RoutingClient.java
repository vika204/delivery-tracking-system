package com.delivery.shipment.geo;

/**
 * Anti-Corruption Layer до Maps/Routing API.
 * <p>
 * Shipment і Pricing працюють лише з внутрішніми типами ({@link Coordinates}, км),
 * а формат конкретного провайдера ховається в реалізації. Зараз підключено
 * {@link OfflineRoutingClient}; реальний провайдер можна підставити, не змінюючи доменний код.
 */
public interface RoutingClient {

    /**
     * Геокодує адресу.
     *
     * @param cityCode    код населеного пункту за KATOTTG (UI передає його з автодоповнення)
     * @param addressLine вулиця й номер будинку
     * @throws AddressNotGeocodableException якщо адресу неможливо визначити
     */
    Coordinates geocode(String cityCode, String addressLine);

    /** Відстань маршруту між точками, км. */
    double routeDistanceKm(Coordinates from, Coordinates to);
}
