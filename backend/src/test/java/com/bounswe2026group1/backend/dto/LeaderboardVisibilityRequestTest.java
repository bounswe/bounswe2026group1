package com.bounswe2026group1.backend.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardVisibilityRequestTest {

    @Test
    void recordExposesValueViaAccessor() {
        LeaderboardVisibilityRequest hidden = new LeaderboardVisibilityRequest(true);
        LeaderboardVisibilityRequest visible = new LeaderboardVisibilityRequest(false);

        assertTrue(hidden.leaderboardHidden());
        assertEquals(Boolean.FALSE, visible.leaderboardHidden());
    }

    @Test
    void recordEqualityIsValueBased() {
        assertEquals(new LeaderboardVisibilityRequest(true), new LeaderboardVisibilityRequest(true));
        assertNotEquals(new LeaderboardVisibilityRequest(true), new LeaderboardVisibilityRequest(false));
    }

    @Test
    void recordAcceptsNullForOptionalConstruction() {
        LeaderboardVisibilityRequest blank = new LeaderboardVisibilityRequest(null);
        assertNull(blank.leaderboardHidden());
    }
}
