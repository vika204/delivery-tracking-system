package com.delivery.dispatch.geo;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Anti-Corruption Layer Dispatch до Maps/Routing API (офлайн-реалізація).
 * <p>
 * Зона обслуговування — область за KATOTTG-кодом (перші дві цифри після "UA");
 * м. Київ і Київська область вважаються однією зоною. Відстань — haversine × коефіцієнт доріг.
 * Алгоритм координат збігається з shipment-service, тож положення населених пунктів однакові.
 */
@Component
public class ServiceZones {

    static final double ROAD_FACTOR = 1.3;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final Pattern KATOTTG = Pattern.compile("UA\\d{17}");
    private static final Set<String> KYIV_ZONE = Set.of("32", "80");

    private static final Map<String, double[]> REGION_CENTRES = Map.ofEntries(
            Map.entry("01", new double[]{44.952, 34.102}),
            Map.entry("05", new double[]{49.233, 28.468}),
            Map.entry("07", new double[]{50.747, 25.325}),
            Map.entry("12", new double[]{48.464, 35.046}),
            Map.entry("14", new double[]{48.015, 37.802}),
            Map.entry("18", new double[]{50.254, 28.658}),
            Map.entry("21", new double[]{48.621, 22.288}),
            Map.entry("23", new double[]{47.838, 35.139}),
            Map.entry("26", new double[]{48.922, 24.711}),
            Map.entry("32", new double[]{50.300, 30.600}),
            Map.entry("35", new double[]{48.508, 32.262}),
            Map.entry("44", new double[]{48.574, 39.307}),
            Map.entry("46", new double[]{49.839, 24.029}),
            Map.entry("48", new double[]{46.975, 31.995}),
            Map.entry("51", new double[]{46.482, 30.723}),
            Map.entry("53", new double[]{49.588, 34.551}),
            Map.entry("56", new double[]{50.619, 26.251}),
            Map.entry("59", new double[]{50.907, 34.798}),
            Map.entry("61", new double[]{49.553, 25.594}),
            Map.entry("63", new double[]{49.994, 36.230}),
            Map.entry("65", new double[]{46.635, 32.616}),
            Map.entry("68", new double[]{49.422, 26.987}),
            Map.entry("71", new double[]{49.444, 32.060}),
            Map.entry("73", new double[]{48.292, 25.935}),
            Map.entry("74", new double[]{51.498, 31.289}),
            Map.entry("80", new double[]{50.450, 30.523}),
            Map.entry("85", new double[]{44.616, 33.525})
    );
    private static final Map<String, Double> REGION_SPREAD_DEGREES = Map.of("80", 0.08, "85", 0.05);
    private static final double DEFAULT_REGION_SPREAD_DEGREES = 0.7;

    public boolean isValidCode(String code) {
        return code != null && KATOTTG.matcher(code).matches() && REGION_CENTRES.containsKey(code.substring(2, 4));
    }

    /** Чи обслуговує кур'єр із зоною {@code courierZoneCode} точку забору {@code pickupCityCode}. */
    public boolean sameZone(String courierZoneCode, String pickupCityCode) {
        if (!isValidCode(courierZoneCode) || !isValidCode(pickupCityCode)) {
            return false;
        }
        String courierRegion = courierZoneCode.substring(2, 4);
        String pickupRegion = pickupCityCode.substring(2, 4);
        return courierRegion.equals(pickupRegion)
                || (KYIV_ZONE.contains(courierRegion) && KYIV_ZONE.contains(pickupRegion));
    }

    /** Орієнтовна позиція центру населеного пункту зони — коли актуальної геопозиції кур'єра немає. */
    public double[] zoneLocation(String zoneCode) {
        String region = zoneCode.substring(2, 4);
        double[] centre = REGION_CENTRES.get(region);
        double spread = REGION_SPREAD_DEGREES.getOrDefault(region, DEFAULT_REGION_SPREAD_DEGREES);
        return new double[]{
                centre[0] + offset(zoneCode, "lat") * spread,
                centre[1] + offset(zoneCode, "lon") * spread * 1.5
        };
    }

    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a)) * ROAD_FACTOR;
        return Math.round(km * 10) / 10.0;
    }

    private static double offset(String value, String salt) {
        CRC32 crc = new CRC32();
        crc.update((salt + ':' + value).getBytes(StandardCharsets.UTF_8));
        return (crc.getValue() % 20_001) / 10_000.0 - 1.0;
    }
}
