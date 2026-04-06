package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import java.util.List;

/*
 * Converts a list of locations to an encoded polyline string with precision 5.
 */
public class PolylineEncoder {

    private PolylineEncoder() {
    }

    public static String encode(List<Location> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int prevLat = 0;
        int prevLng = 0;

        for (Location location : path) {
            int lat = (int) Math.round(location.getLatitude() * 1e5);
            int lng = (int) Math.round(location.getLongitude() * 1e5);

            int dLat = lat - prevLat;
            int dLng = lng - prevLng;

            prevLat = lat;
            prevLng = lng;

            encodeValue(dLat, result);
            encodeValue(dLng, result);
        }

        return result.toString();
    }

    private static void encodeValue(int value, StringBuilder result) {
        value = value < 0 ? ~(value << 1) : (value << 1);
        while (value >= 0x20) {
            int nextValue = (0x20 | (value & 0x1f)) + 63;
            result.append((char) nextValue);
            value >>= 5;
        }
        result.append((char) (value + 63));
    }
}
