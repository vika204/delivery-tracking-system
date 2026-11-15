package com.delivery.dispatch.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceZonesTest {

    private static final String KYIV_CITY = "UA80000000000093317";
    private static final String KYIV_OBLAST_BROVARY = "UA32020090010010446";
    private static final String LVIV = "UA46060250010015970";

    private final ServiceZones zones = new ServiceZones();

    @Test
    void kyivCityAndKyivOblastAreOneZone() {
        assertThat(zones.sameZone(KYIV_CITY, KYIV_OBLAST_BROVARY)).isTrue();
        assertThat(zones.sameZone(KYIV_OBLAST_BROVARY, KYIV_CITY)).isTrue();
    }

    @Test
    void differentRegionsAreDifferentZones() {
        assertThat(zones.sameZone(KYIV_CITY, LVIV)).isFalse();
    }

    @Test
    void invalidCodesNeverMatch() {
        assertThat(zones.sameZone(null, KYIV_CITY)).isFalse();
        assertThat(zones.sameZone("Kyiv", KYIV_CITY)).isFalse();
        assertThat(zones.sameZone("UA99000000000000000", "UA99000000000000001")).isFalse();
    }

    @Test
    void zoneLocationIsDeterministicAndNearRegionCentre() {
        double[] first = zones.zoneLocation(KYIV_CITY);
        double[] second = zones.zoneLocation(KYIV_CITY);

        assertThat(first).containsExactly(second);
        assertThat(zones.distanceKm(first[0], first[1], 50.45, 30.523)).isLessThan(20);
    }

    @Test
    void distanceUsesRoadFactor() {
        // Kyiv -> Lviv ≈ 470 km great circle
        double km = zones.distanceKm(50.450, 30.523, 49.839, 24.029);

        assertThat(km).isBetween(560.0, 660.0);
    }
}
