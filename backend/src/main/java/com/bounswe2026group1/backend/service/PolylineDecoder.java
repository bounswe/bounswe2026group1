package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;

import java.util.ArrayList;
import java.util.List;

/*
 * Converts encoded polyline to list of locations (lat, lon) with precision 5
 * Spec:
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */

public class PolylineDecoder {

    private PolylineDecoder() {
    }

    public static List<Location> decode(String encoded) {
        List<Location> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }

        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int result = 0;
            int shift = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            result = 0;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            points.add(new Location(lat / 1e5, lng / 1e5));
        }
        return points;
    }
}
