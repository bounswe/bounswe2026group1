package com.bounswe2026group1.backend.model;

/**
 * Audit reason on every {@link PointEvent}. The set of reasons is the
 * full enumeration of point-changing events in the gamification rules,
 * so that the running balance on {@link RegisteredUser#getPoints()}
 * can always be reconstructed from the ledger.
 */
public enum PointReason {

    /** Author submitted a new report. */
    REPORT_SUBMIT,

    /** A vote was cast on someone else's report. */
    VOTE_CAST,

    /** A previously cast vote was fully withdrawn. */
    VOTE_WITHDRAWN,

    /** Author bonus when their report reaches VERIFIED. */
    REPORT_VERIFIED_BONUS,

    /** Author penalty when their report reaches REJECTED. */
    REPORT_REJECTED_PENALTY,

    /** Voter reward when their vote agreed with the final status. */
    VOTE_ALIGNED,

    /** Voter penalty when their vote opposed the final status. */
    VOTE_OPPOSED
}
