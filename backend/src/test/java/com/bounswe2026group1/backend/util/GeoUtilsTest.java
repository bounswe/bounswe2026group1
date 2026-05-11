package com.bounswe2026group1.backend.util;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeoUtilsTest {

    @Test
    void point4326_swapsToJtsXyOrdering_xIsLongitude_yIsLatitude() {
        Point p = GeoUtils.point4326(41.015137, 28.979530); // Istanbul

        assertNotNull(p);
        assertEquals(28.979530, p.getX(), 1e-9, "x must be longitude");
        assertEquals(41.015137, p.getY(), 1e-9, "y must be latitude");
    }

    @Test
    void point4326_setsSrid4326() {
        Point p = GeoUtils.point4326(0.0, 0.0);
        assertEquals(4326, p.getSRID());
    }

    @Test
    void point4326_handlesNegativeAndZeroCoordinates() {
        Point south = GeoUtils.point4326(-33.865143, 151.209900); // Sydney
        assertEquals(151.209900, south.getX(), 1e-9);
        assertEquals(-33.865143, south.getY(), 1e-9);

        Point origin = GeoUtils.point4326(0.0, 0.0);
        assertEquals(0.0, origin.getX());
        assertEquals(0.0, origin.getY());
    }
}
