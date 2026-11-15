package com.delivery.shipment.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfflineRoutingClientTest {

    static final String KYIV = "UA80000000000093317";
    static final String LVIV = "UA46060250010015970";

    private final OfflineRoutingClient client = new OfflineRoutingClient();

    @Test
    void geocodingIsDeterministic() {
        assertThat(client.geocode(KYIV, "Khreshchatyk 1")).isEqualTo(client.geocode(KYIV, "  khreshchatyk   1 "));
    }

    @Test
    void addressesInsideKyivAreCloseToEachOther() {
        Coordinates a = client.geocode(KYIV, "Khreshchatyk 1");
        Coordinates b = client.geocode(KYIV, "Saksahanskoho 28");

        assertThat(client.routeDistanceKm(a, b)).isBetween(OfflineRoutingClient.MIN_DISTANCE_KM, 15.0);
    }

    @Test
    void kyivToLvivDistanceIsRealistic() {
        double km = client.routeDistanceKm(client.geocode(KYIV, "Khreshchatyk 1"), client.geocode(LVIV, "Svobody 10"));

        // реальна дорога ≈ 540 км
        assertThat(km).isBetween(400.0, 750.0);
    }

    @Test
    void unknownSettlementCodeIsRejected() {
        assertThatThrownBy(() -> client.geocode("Kyiv", "Khreshchatyk 1"))
                .isInstanceOf(AddressNotGeocodableException.class);
        assertThatThrownBy(() -> client.geocode("UA99000000000000000", "Khreshchatyk 1"))
                .isInstanceOf(AddressNotGeocodableException.class);
    }

    @Test
    void streetIsRequired() {
        assertThatThrownBy(() -> client.geocode(KYIV, " "))
                .isInstanceOf(AddressNotGeocodableException.class);
    }
}
