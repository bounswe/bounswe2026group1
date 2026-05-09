package com.bounswe2026group1.backend.model;

import java.util.Collection;
import java.util.Comparator;

/**
 * Gamification badges.
 *
 * Tier ordering matches {@link #getTier()} — used to surface a single
 * "highest badge" next to the author's name in report panels. Higher
 * tier wins; ties never occur because each user holds at most one
 * row per badge.
 */
public enum Badge {
    /** Awarded once a user reaches 10 verified reports. Permanent. */
    TRUSTED_REPORTER(1),

    /** Awarded once a user reaches 50 verified reports. Permanent. */
    EXPERT_MAPPER(2),

    /**
     * Awarded to the current top-10 leaderboard holders. Revoked the moment
     * the user drops below rank 10.
     */
    TOP_10(3);

    private final int tier;

    Badge(int tier) {
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /** Highest-tier badge in a collection, or null if the collection is null
     *  or empty. Used wherever a UI surfaces a single "primary" badge for
     *  a user (profile DTO, report-panel author chip). */
    public static Badge pickHighestTier(Collection<Badge> badges) {
        if (badges == null || badges.isEmpty()) return null;
        return badges.stream().max(Comparator.comparingInt(Badge::getTier)).orElse(null);
    }
}
