package com.bounswe2026group1.backend.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeTest {

    @Test
    void pickHighestTier_returnsHighestTierBadge() {
        // Tiers are TRUSTED_REPORTER(1) < EXPERT_MAPPER(2) < TOP_10(3).
        Badge top = Badge.pickHighestTier(List.of(Badge.TRUSTED_REPORTER, Badge.TOP_10, Badge.EXPERT_MAPPER));
        assertThat(top).isEqualTo(Badge.TOP_10);
    }

    @Test
    void pickHighestTier_singleBadgeReturnsItself() {
        assertThat(Badge.pickHighestTier(List.of(Badge.TRUSTED_REPORTER)))
                .isEqualTo(Badge.TRUSTED_REPORTER);
    }

    @Test
    void pickHighestTier_emptyCollectionReturnsNull() {
        assertThat(Badge.pickHighestTier(List.of())).isNull();
    }

    @Test
    void pickHighestTier_nullReturnsNull() {
        assertThat(Badge.pickHighestTier(null)).isNull();
    }
}
