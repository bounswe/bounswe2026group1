package com.bounswe2026group1.backend.model;

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
}
