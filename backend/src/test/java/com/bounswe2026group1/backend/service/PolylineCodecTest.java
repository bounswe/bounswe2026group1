package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolylineCodecTest {

    @Test
    void decoder_returnsEmptyForNullOrBlank() {
        assertTrue(PolylineDecoder.decode(null).isEmpty());
        assertTrue(PolylineDecoder.decode("").isEmpty());
    }

    @Test
    void encoder_returnsEmptyForNullOrEmptyPath() {
        assertEquals("", PolylineEncoder.encode(null));
        assertEquals("", PolylineEncoder.encode(List.of()));
    }

    @Test
    void encodeThenDecode_roundTripsSinglePoint() {
        List<Location> original = List.of(new Location(41.0082, 28.9784));
        String encoded = PolylineEncoder.encode(original);
        List<Location> decoded = PolylineDecoder.decode(encoded);

        assertEquals(1, decoded.size());
        assertEquals(original.getFirst().getLatitude(), decoded.getFirst().getLatitude(), 1e-5);
        assertEquals(original.getFirst().getLongitude(), decoded.getFirst().getLongitude(), 1e-5);
    }

    @Test
    void encodeThenDecode_roundTripsMultiplePoints() {
        List<Location> original = List.of(
                new Location(41.0, 29.0),
                new Location(41.01, 29.02),
                new Location(41.02, 29.04));
        String encoded = PolylineEncoder.encode(original);
        List<Location> decoded = PolylineDecoder.decode(encoded);

        assertEquals(original.size(), decoded.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).getLatitude(), decoded.get(i).getLatitude(), 1e-5);
            assertEquals(original.get(i).getLongitude(), decoded.get(i).getLongitude(), 1e-5);
        }
    }
}
