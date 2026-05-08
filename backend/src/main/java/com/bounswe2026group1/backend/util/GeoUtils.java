package com.bounswe2026group1.backend.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Helpers for WGS‑84 (EPSG:4326) points. JTS uses (x = longitude, y = latitude).
 */
public final class GeoUtils {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoUtils() {}

    public static Point point4326(double latitude, double longitude) {
        Point p = FACTORY.createPoint(new Coordinate(longitude, latitude));
        p.setSRID(4326);
        return p;
    }
}
