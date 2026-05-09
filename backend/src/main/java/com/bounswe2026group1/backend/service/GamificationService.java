package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.PointEvent;
import com.bounswe2026group1.backend.model.PointReason;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.UserBadge;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.PointEventRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import com.bounswe2026group1.backend.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns every gamification side-effect: point award/deduct, ledger writes,
 * milestone badge evaluation. Callers (ReportService, FixRequestService)
 * invoke high-level methods like {@link #awardOnReportSubmit} — never
 * {@link #applyDelta} directly — so all rule changes stay localised here.
 *
 * Top-10 badges are owned by LeaderboardService once it lands; this
 * service does not touch the TOP_10 badge.
 */
@Service
@RequiredArgsConstructor
public class GamificationService {

    private final RegisteredUserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ReportVerificationRepository verificationRepository;
    private final PointEventRepository pointEventRepository;
    private final UserBadgeRepository userBadgeRepository;

    @Value("${app.gamification.points.report-submit:10}")
    private int reportSubmitDelta;

    @Value("${app.gamification.points.vote-cast:5}")
    private int voteCastDelta;

    @Value("${app.gamification.points.vote-withdrawn:-5}")
    private int voteWithdrawnDelta;

    @Value("${app.gamification.points.verified-bonus:20}")
    private int verifiedBonusDelta;

    @Value("${app.gamification.points.rejected-penalty:-15}")
    private int rejectedPenaltyDelta;

    @Value("${app.gamification.points.vote-aligned:15}")
    private int voteAlignedDelta;

    @Value("${app.gamification.points.vote-opposed:-5}")
    private int voteOpposedDelta;

    @Value("${app.gamification.badges.trusted-reporter-threshold:10}")
    private int trustedReporterThreshold;

    @Value("${app.gamification.badges.expert-mapper-threshold:50}")
    private int expertMapperThreshold;

    // ────────────────────────── Public API ──────────────────────────────

    /** Author submitted a new report. Awarded once per report (callers
     *  fire this exactly once from ReportService.create). */
    @Transactional
    public void awardOnReportSubmit(RegisteredUser author, Long reportId) {
        if (author == null) return;
        applyDelta(author, reportSubmitDelta, PointReason.REPORT_SUBMIT, reportId);
    }

    /**
     * Voter cast a fresh vote on someone else's report. Modifying an
     * existing vote (agree → disagree or vice versa) MUST NOT yield
     * extra points; callers handle that by NOT calling this method on
     * modify, only on first cast.
     *
     * The cast/withdraw cycle is allowed to net out: a user who casts (+5),
     * withdraws (-5), and casts again receives a fresh +5. We detect that
     * by comparing CAST and WITHDRAWN event counts on (user, report) — if
     * casts already exceed withdrawals, the user is currently in "voted"
     * state and another call here is a modify, not a fresh cast.
     */
    @Transactional
    public void awardOnVoteCast(RegisteredUser voter, Long reportId) {
        if (voter == null || reportId == null) return;
        if (isCurrentlyVoted(voter.getId(), reportId)) {
            return; // Modify-vote path, no extra points.
        }
        applyDelta(voter, voteCastDelta, PointReason.VOTE_CAST, reportId);
    }

    /** Voter fully withdrew their previously cast vote. */
    @Transactional
    public void deductOnVoteWithdraw(RegisteredUser voter, Long reportId) {
        if (voter == null || reportId == null) return;
        if (!isCurrentlyVoted(voter.getId(), reportId)) {
            // Defensive: never deduct points for a withdraw the user never
            // paired with a cast (would otherwise let a malicious caller
            // drive any user to zero via spurious withdraw events).
            return;
        }
        applyDelta(voter, voteWithdrawnDelta, PointReason.VOTE_WITHDRAWN, reportId);
    }

    /**
     * A report transitioned to its terminal status. Apply author bonus
     * or penalty and reward/penalise voters by alignment.
     *
     * <p>Per the agreed plan, only PENDING→VERIFIED and PENDING→REJECTED
     * transitions trigger this; reverts (VERIFIED→PENDING, REJECTED→PENDING)
     * deliberately do NOT undo points, to keep the audit ledger stable
     * and the leaderboard predictable.
     */
    @Transactional
    public void onReportStatusTransition(Report report, ReportStatus from, ReportStatus to) {
        if (report == null || from == to) return;
        if (from != ReportStatus.PENDING) return;
        if (to != ReportStatus.VERIFIED && to != ReportStatus.REJECTED) return;

        boolean verified = (to == ReportStatus.VERIFIED);

        // 1) Author bonus or penalty.
        RegisteredUser author = report.getCreatedBy();
        if (author != null) {
            int delta = verified ? verifiedBonusDelta : rejectedPenaltyDelta;
            PointReason reason = verified
                    ? PointReason.REPORT_VERIFIED_BONUS
                    : PointReason.REPORT_REJECTED_PENALTY;
            applyDelta(author, delta, reason, report.getReportId());

            // Milestone badges only matter for VERIFIED transitions, since the
            // count we evaluate is verified-reports. Cheap to skip on REJECTED.
            if (verified) {
                evaluateMilestoneBadges(author);
            }
        }

        // 2) Voter alignment payout.
        VoteType winningSide = verified ? VoteType.AGREE : VoteType.DISAGREE;
        List<Object[]> voteTuples = verificationRepository
                .findVoteTuplesByReportId(report.getReportId());
        if (voteTuples.isEmpty()) return;

        List<Long> voterIds = voteTuples.stream()
                .map(row -> (Long) row[0])
                .filter(id -> author == null || !id.equals(author.getId()))
                .toList();
        if (voterIds.isEmpty()) return;

        Map<Long, RegisteredUser> votersById = userRepository.findAllById(voterIds).stream()
                .collect(Collectors.toMap(RegisteredUser::getId, Function.identity()));

        for (Object[] row : voteTuples) {
            Long voterId = (Long) row[0];
            VoteType voteType = (VoteType) row[1];
            if (author != null && voterId.equals(author.getId())) continue;

            RegisteredUser voter = votersById.get(voterId);
            if (voter == null) continue;

            boolean aligned = (voteType == winningSide);
            int delta = aligned ? voteAlignedDelta : voteOpposedDelta;
            PointReason reason = aligned ? PointReason.VOTE_ALIGNED : PointReason.VOTE_OPPOSED;
            applyDelta(voter, delta, reason, report.getReportId());
        }
    }

    /**
     * Re-evaluates Trusted Reporter / Expert Mapper milestone badges for
     * a user. Idempotent: badges already held are skipped, and a
     * transient drop in verified-count (e.g. one report later REJECTED)
     * does NOT revoke milestone badges — the rule says "automatically
     * award", revocation language only applies to TOP_10.
     */
    @Transactional
    public List<Badge> evaluateMilestoneBadges(RegisteredUser user) {
        if (user == null) return List.of();
        long verifiedCount = reportRepository
                .countByCreatedByIdAndStatus(user.getId(), ReportStatus.VERIFIED);

        List<Badge> newlyAwarded = new ArrayList<>();
        if (verifiedCount >= trustedReporterThreshold) {
            if (awardBadgeIfMissing(user, Badge.TRUSTED_REPORTER)) {
                newlyAwarded.add(Badge.TRUSTED_REPORTER);
            }
        }
        if (verifiedCount >= expertMapperThreshold) {
            if (awardBadgeIfMissing(user, Badge.EXPERT_MAPPER)) {
                newlyAwarded.add(Badge.EXPERT_MAPPER);
            }
        }
        return newlyAwarded;
    }

    // ────────────────────────── Internals ───────────────────────────────

    /**
     * Single point of mutation for the user's points field. Clamps the
     * resulting balance at zero and writes the delta that was actually
     * applied to the ledger, so summing ledger deltas always
     * reconstructs the running balance.
     */
    void applyDelta(RegisteredUser user, int requestedDelta, PointReason reason, Long relatedId) {
        if (user == null || requestedDelta == 0) return;

        int before = user.getPoints();
        int after = Math.max(0, before + requestedDelta);
        int actualDelta = after - before;
        if (actualDelta == 0) return;

        // Persist the user balance and the ledger entry together. We log
        // the actualDelta (post-clamp), so summing the ledger always
        // reconstructs the running balance — no "absorbed by clamp" rows.
        user.setPoints(after);
        userRepository.save(user);
        pointEventRepository.save(new PointEvent(user, actualDelta, reason, relatedId));
    }

    private boolean awardBadgeIfMissing(RegisteredUser user, Badge badge) {
        if (userBadgeRepository.existsByUserIdAndBadge(user.getId(), badge)) {
            return false;
        }
        userBadgeRepository.save(new UserBadge(user, badge));
        return true;
    }

    /**
     * True iff the user currently holds an un-withdrawn vote on this report,
     * computed from the ledger so we don't have to add a flag column on
     * {@link com.bounswe2026group1.backend.model.ReportVerification}.
     * Each cast inserts a VOTE_CAST event; each withdraw inserts a
     * VOTE_WITHDRAWN event. The user is "currently voted" when CAST count
     * exceeds WITHDRAWN count.
     */
    private boolean isCurrentlyVoted(Long userId, Long reportId) {
        long casts = pointEventRepository
                .countByUserIdAndRelatedEntityIdAndReason(userId, reportId, PointReason.VOTE_CAST);
        long withdrawals = pointEventRepository
                .countByUserIdAndRelatedEntityIdAndReason(userId, reportId, PointReason.VOTE_WITHDRAWN);
        return casts > withdrawals;
    }
}
