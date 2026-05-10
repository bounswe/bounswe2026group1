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
    VOTE_OPPOSED,

    /** A vote was cast on a fix request. Symmetric to VOTE_CAST but tracked
     *  separately so the cast/withdraw cycle for fix requests is paired
     *  against its own ledger entries, not against report votes. */
    FIX_REQUEST_VOTE_CAST,

    /** A previously cast fix-request vote was fully withdrawn. */
    FIX_REQUEST_VOTE_WITHDRAWN,

    /** Bonus to the fix-request submitter when their request reaches
     *  RESOLVED_FIXED — analogous to REPORT_VERIFIED_BONUS but for the
     *  person who proved the report is no longer an obstacle. */
    FIX_RESOLVED_BONUS,

    /** Voter reward when their fix-request vote agreed with the resolved
     *  outcome (agree → request resolved as fixed). */
    FIX_VOTE_ALIGNED,

    /** Voter penalty when their fix-request vote opposed the resolved
     *  outcome (disagree → request still resolved as fixed). */
    FIX_VOTE_OPPOSED
}
