package com.bounswe2026group1.backend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapTypeTest {

    @Test
    void valuesReturnsSingleSupportedKind() {
        assertArrayEquals(new SnapType[]{SnapType.STAIR}, SnapType.values());
    }

    @Test
    void valueOfRoundTripsByName() {
        assertEquals(SnapType.STAIR, SnapType.valueOf("STAIR"));
    }
}
