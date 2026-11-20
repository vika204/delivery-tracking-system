package com.delivery.shipment.geo;

import com.delivery.shipment.exception.AddressNotGeocodableException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class OfflineRoutingClient implements RoutingClient {

    static final double ROAD_FACTOR = 1.3;
    static final double MIN_DISTANCE_KM = 1.0;

    private static final Pattern KATOTTG = Pattern.compile("UA\\d{17}");
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final Map<String, Coordinates> REGION_CENTRES = Map.ofEntries(
            Map.entry("01", new Coordinates(44.952, 34.102)),
            Map.entry("05", new Coordinates(49.233, 28.468)),
            Map.entry("07", new Coordinates(50.747, 25.325)),
            Map.entry("12", new Coordinates(48.464, 35.046)),
            Map.entry("14", new Coordinates(48.015, 37.802)),
            Map.entry("18", new Coordinates(50.254, 28.658)),
            Map.entry("21", new Coordinates(48.621, 22.288)),
            Map.entry("23", new Coordinates(47.838, 35.139)),
            Map.entry("26", new Coordinates(48.922, 24.711)),
            Map.entry("32", new Coordinates(50.300, 30.600)),
            Map.entry("35", new Coordinates(48.508, 32.262)),
            Map.entry("44", new Coordinates(48.574, 39.307)),
            Map.entry("46", new Coordinates(49.839, 24.029)),
            Map.entry("48", new Coordinates(46.975, 31.995)),
            Map.entry("51", new Coordinates(46.482, 30.723)),
            Map.entry("53", new Coordinates(49.588, 34.551)),
            Map.entry("56", new Coordinates(50.619, 26.251)),
            Map.entry("59", new Coordinates(50.907, 34.798)),
            Map.entry("61", new Coordinates(49.553, 25.594)),
            Map.entry("63", new Coordinates(49.994, 36.230)),
            Map.entry("65", new Coordinates(46.635, 32.616)),
            Map.entry("68", new Coordinates(49.422, 26.987)),
            Map.entry("71", new Coordinates(49.444, 32.060)),
            Map.entry("73", new Coordinates(48.292, 25.935)),
            Map.entry("74", new Coordinates(51.498, 31.289)),
            Map.entry("80", new Coordinates(50.450, 30.523)),
            Map.entry("85", new Coordinates(44.616, 33.525))
    );

    private static final Map<String, Double> REGION_SPREAD_DEGREES = Map.of("80", 0.08, "85", 0.05);
    private static final double DEFAULT_REGION_SPREAD_DEGREES = 0.7;
    private static final double ADDRESS_SPREAD_DEGREES = 0.02;

    @Override
    public Coordinates geocode(String cityCode, String addressLine) {
        if (cityCode == null || !KATOTTG.matcher(cityCode).matches()) {
            throw new AddressNotGeocodableException("Unknown settlement code: " + cityCode);
        }
        if (addressLine == null || addressLine.isBlank()) {
            throw new AddressNotGeocodableException("Street and house number are required");
        }

        String region = cityCode.substring(2, 4);
        Coordinates centre = REGION_CENTRES.get(region);
        if (centre == null) {
            throw new AddressNotGeocodableException("Unknown region in settlement code: " + cityCode);
        }

        double regionSpread = REGION_SPREAD_DEGREES.getOrDefault(region, DEFAULT_REGION_SPREAD_DEGREES);
        double latitude = centre.latitude()
                + offset(cityCode, "lat") * regionSpread
                + offset(normalize(addressLine), "lat") * ADDRESS_SPREAD_DEGREES;
        double longitude = centre.longitude()
                + offset(cityCode, "lon") * regionSpread * 1.5
                + offset(normalize(addressLine), "lon") * ADDRESS_SPREAD_DEGREES * 1.5;

        return new Coordinates(round(latitude, 6), round(longitude, 6));
    }

    @Override
    public double routeDistanceKm(Coordinates from, Coordinates to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double greatCircleKm = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));

        return round(Math.max(MIN_DISTANCE_KM, greatCircleKm * ROAD_FACTOR), 1);
    }

    private static double offset(String value, String salt) {
        CRC32 crc = new CRC32();
        crc.update((salt + ':' + value).getBytes(StandardCharsets.UTF_8));
        return (crc.getValue() % 20_001) / 10_000.0 - 1.0;
    }

    private static String normalize(String addressLine) {
        return addressLine.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
