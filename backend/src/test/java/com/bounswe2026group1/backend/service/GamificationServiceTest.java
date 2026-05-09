package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.PointEvent;
import com.bounswe2026group1.backend.model.PointReason;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserBadge;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.PointEventRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import com.bounswe2026group1.backend.repository.UserBadgeRepository;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock private RegisteredUserRepository userRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportVerificationRepository verificationRepository;
    @Mock private PointEventRepository pointEventRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private LeaderboardService leaderboardService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private GamificationService gamificationService;

    @BeforeEach
    void setUp() {
        // Mirror application.properties defaults so the service has live values
        // when invoked from the unit-test container.
        ReflectionTestUtils.setField(gamificationService, "reportSubmitDelta", 10);
        ReflectionTestUtils.setField(gamificationService, "voteCastDelta", 5);
        ReflectionTestUtils.setField(gamificationService, "voteWithdrawnDelta", -5);
        ReflectionTestUtils.setField(gamificationService, "verifiedBonusDelta", 20);
        ReflectionTestUtils.setField(gamificationService, "rejectedPenaltyDelta", -15);
        ReflectionTestUtils.setField(gamificationService, "voteAlignedDelta", 15);
        ReflectionTestUtils.setField(gamificationService, "voteOpposedDelta", -5);
        ReflectionTestUtils.setField(gamificationService, "trustedReporterThreshold", 10);
        ReflectionTestUtils.setField(gamificationService, "expertMapperThreshold", 50);
    }

    // ───────────────────── awardOnReportSubmit ──────────────────────────

    @Test
    void awardOnReportSubmit_addsTenPointsAndLogsEvent() {
        RegisteredUser author = newUser(1L, 0);

        gamificationService.awardOnReportSubmit(author, 100L);

        assertThat(author.getPoints()).isEqualTo(10);
        verify(userRepository).save(author);
        ArgumentCaptor<PointEvent> ev = ArgumentCaptor.forClass(PointEvent.class);
        verify(pointEventRepository).save(ev.capture());
        assertThat(ev.getValue().getDelta()).isEqualTo(10);
        assertThat(ev.getValue().getReason()).isEqualTo(PointReason.REPORT_SUBMIT);
        assertThat(ev.getValue().getRelatedEntityId()).isEqualTo(100L);
    }

    @Test
    void awardOnReportSubmit_nullAuthorIsNoOp() {
        gamificationService.awardOnReportSubmit(null, 100L);
        verify(userRepository, never()).save(any());
        verify(pointEventRepository, never()).save(any());
        verify(leaderboardService, never()).onPointsChanged(any());
    }

    @Test
    void applyDelta_triggersLeaderboardRecompute() {
        // Top-10 badge churn must track every point change in real time —
        // applyDelta is the single mutation point, so any award/deduct
        // method on the public API ends up triggering this.
        RegisteredUser author = newUser(1L, 0);

        gamificationService.awardOnReportSubmit(author, 100L);

        verify(leaderboardService).onPointsChanged(1L);
    }

    // ───────────────────────── awardOnVoteCast ──────────────────────────

    @Test
    void awardOnVoteCast_addsFivePointsOnFreshVote() {
        RegisteredUser voter = newUser(2L, 100);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_CAST)).thenReturn(0L);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_WITHDRAWN)).thenReturn(0L);

        gamificationService.awardOnVoteCast(voter, 100L);

        assertThat(voter.getPoints()).isEqualTo(105);
        verify(pointEventRepository).save(any(PointEvent.class));
    }

    @Test
    void awardOnVoteCast_isNoOpWhenAlreadyVoted_modifyVotePath() {
        // Modify-vote (agree → disagree or vice versa) must NOT yield extra points.
        RegisteredUser voter = newUser(2L, 100);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_CAST)).thenReturn(1L);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_WITHDRAWN)).thenReturn(0L);

        gamificationService.awardOnVoteCast(voter, 100L);

        assertThat(voter.getPoints()).isEqualTo(100);
        verify(pointEventRepository, never()).save(any(PointEvent.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void awardOnVoteCast_addsPointsAfterWithdraw_recastIsLegitimate() {
        // cycle: cast(+5) → withdraw(-5) → cast(+5). Casts 1, withdrawals 1
        // → currently NOT voted → fresh cast pays.
        RegisteredUser voter = newUser(2L, 100);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_CAST)).thenReturn(1L);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_WITHDRAWN)).thenReturn(1L);

        gamificationService.awardOnVoteCast(voter, 100L);

        assertThat(voter.getPoints()).isEqualTo(105);
        verify(pointEventRepository).save(any(PointEvent.class));
    }

    // ──────────────────────── deductOnVoteWithdraw ─────────────────────

    @Test
    void deductOnVoteWithdraw_subtractsFiveOnActiveVote() {
        RegisteredUser voter = newUser(2L, 100);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_CAST)).thenReturn(1L);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_WITHDRAWN)).thenReturn(0L);

        gamificationService.deductOnVoteWithdraw(voter, 100L);

        assertThat(voter.getPoints()).isEqualTo(95);
        ArgumentCaptor<PointEvent> ev = ArgumentCaptor.forClass(PointEvent.class);
        verify(pointEventRepository).save(ev.capture());
        assertThat(ev.getValue().getReason()).isEqualTo(PointReason.VOTE_WITHDRAWN);
        assertThat(ev.getValue().getDelta()).isEqualTo(-5);
    }

    @Test
    void deductOnVoteWithdraw_isNoOpWhenNoActiveVote() {
        // Defensive: never deduct for a withdraw without a paired cast.
        RegisteredUser voter = newUser(2L, 100);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_CAST)).thenReturn(1L);
        when(pointEventRepository.countByUserIdAndRelatedEntityIdAndReason(
                2L, 100L, PointReason.VOTE_WITHDRAWN)).thenReturn(1L);

        gamificationService.deductOnVoteWithdraw(voter, 100L);

        assertThat(voter.getPoints()).isEqualTo(100);
        verify(pointEventRepository, never()).save(any(PointEvent.class));
    }

    // ─────────────────────── score-clamp at zero ────────────────────────

    @Test
    void applyDelta_clampsAtZero_andLedgerRecordsActualDelta() {
        // User has 10 points, gets a -15 penalty → clamped to 0. Ledger
        // delta should be -10 (the amount actually applied), not -15.
        RegisteredUser author = newUser(1L, 10);
        Report report = stubReport(author);

        when(verificationRepository.findVoteTuplesByReportId(report.getReportId()))
                .thenReturn(List.of());

        gamificationService.onReportStatusTransition(report, ReportStatus.PENDING, ReportStatus.REJECTED);

        assertThat(author.getPoints()).isEqualTo(0);
        ArgumentCaptor<PointEvent> ev = ArgumentCaptor.forClass(PointEvent.class);
        verify(pointEventRepository).save(ev.capture());
        assertThat(ev.getValue().getDelta()).isEqualTo(-10);
    }

    @Test
    void applyDelta_clampedToNoOpDoesNotPersist() {
        // User already at 0, another penalty applied → no-op, no save.
        RegisteredUser author = newUser(1L, 0);
        Report report = stubReport(author);
        when(verificationRepository.findVoteTuplesByReportId(report.getReportId()))
                .thenReturn(List.of());

        gamificationService.onReportStatusTransition(report, ReportStatus.PENDING, ReportStatus.REJECTED);

        assertThat(author.getPoints()).isEqualTo(0);
        verify(pointEventRepository, never()).save(any(PointEvent.class));
        verify(userRepository, never()).save(any());
    }

    // ────────────────────── status-transition fan-out ───────────────────

    @Test
    void onReportStatusTransition_verified_paysAuthorBonusAndAlignsVoters() {
        RegisteredUser author = newUser(1L, 0);
        RegisteredUser agreer = newUser(2L, 0);
        RegisteredUser disagreer = newUser(3L, 100);
        Report report = stubReport(author);

        when(verificationRepository.findVoteTuplesByReportId(report.getReportId()))
                .thenReturn(List.of(
                        new Object[]{2L, VoteType.AGREE},
                        new Object[]{3L, VoteType.DISAGREE}
                ));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(agreer, disagreer));
        // Author has 0 verified reports — milestone evaluation runs but no badge yet.
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(0L);

        gamificationService.onReportStatusTransition(report, ReportStatus.PENDING, ReportStatus.VERIFIED);

        assertThat(author.getPoints()).isEqualTo(20);    // verified-bonus
        assertThat(agreer.getPoints()).isEqualTo(15);    // aligned (+15)
        assertThat(disagreer.getPoints()).isEqualTo(95); // opposed (-5)
        verify(pointEventRepository, times(3)).save(any(PointEvent.class));
    }

    @Test
    void onReportStatusTransition_rejected_penalisesAuthorAndFlipsAlignment() {
        RegisteredUser author = newUser(1L, 100);
        RegisteredUser agreer = newUser(2L, 100);
        RegisteredUser disagreer = newUser(3L, 0);
        Report report = stubReport(author);

        when(verificationRepository.findVoteTuplesByReportId(report.getReportId()))
                .thenReturn(List.of(
                        new Object[]{2L, VoteType.AGREE},
                        new Object[]{3L, VoteType.DISAGREE}
                ));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(agreer, disagreer));

        gamificationService.onReportStatusTransition(report, ReportStatus.PENDING, ReportStatus.REJECTED);

        assertThat(author.getPoints()).isEqualTo(85);    // 100 - 15
        assertThat(agreer.getPoints()).isEqualTo(95);    // -5 opposed
        assertThat(disagreer.getPoints()).isEqualTo(15); // +15 aligned
    }

    @Test
    void onReportStatusTransition_skipsRevertsToPending() {
        // VERIFIED → PENDING should NOT undo the original payout (per the
        // agreed plan: only PENDING → terminal transitions trigger).
        RegisteredUser author = newUser(1L, 50);
        Report report = stubReport(author);

        gamificationService.onReportStatusTransition(report, ReportStatus.VERIFIED, ReportStatus.PENDING);

        assertThat(author.getPoints()).isEqualTo(50);
        verify(pointEventRepository, never()).save(any(PointEvent.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void onReportStatusTransition_skipsAuthorFromVoterFanOut() {
        // The system blocks self-vote at the controller layer, but be
        // defensive: if a row somehow exists, don't double-pay.
        RegisteredUser author = newUser(1L, 0);
        Report report = stubReport(author);

        when(verificationRepository.findVoteTuplesByReportId(report.getReportId()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, VoteType.AGREE}));
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(0L);

        gamificationService.onReportStatusTransition(report, ReportStatus.PENDING, ReportStatus.VERIFIED);

        // Author paid only the +20 bonus, never the +15 voter-aligned reward.
        assertThat(author.getPoints()).isEqualTo(20);
        verify(pointEventRepository, times(1)).save(any(PointEvent.class));
    }

    // ─────────────────────── milestone badges ───────────────────────────

    @Test
    void evaluateMilestoneBadges_awardsTrustedAtThreshold() {
        RegisteredUser user = newUser(1L, 0);
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(10L);
        when(userBadgeRepository.existsByUserIdAndBadge(1L, Badge.TRUSTED_REPORTER))
                .thenReturn(false);
        // Expert-mapper threshold (50) not crossed at 10 verified — its
        // existsBy stub would be unused, so we deliberately omit it.

        List<Badge> awarded = gamificationService.evaluateMilestoneBadges(user);

        assertThat(awarded).containsExactly(Badge.TRUSTED_REPORTER);
        verify(userBadgeRepository, times(1)).save(any(UserBadge.class));
        // Each newly awarded milestone fires a BADGE_AWARDED notification.
        verify(notificationService).notifyBadgeAwarded(user, Badge.TRUSTED_REPORTER);
    }

    @Test
    void evaluateMilestoneBadges_existingBadgeDoesNotReFireNotification() {
        // Idempotent path: badge already held → no save, no notification.
        RegisteredUser user = newUser(1L, 0);
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(15L);
        when(userBadgeRepository.existsByUserIdAndBadge(1L, Badge.TRUSTED_REPORTER))
                .thenReturn(true);

        gamificationService.evaluateMilestoneBadges(user);

        verify(notificationService, never()).notifyBadgeAwarded(any(), any());
    }

    @Test
    void evaluateMilestoneBadges_awardsBothAtExpertThreshold() {
        RegisteredUser user = newUser(1L, 0);
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(50L);
        when(userBadgeRepository.existsByUserIdAndBadge(1L, Badge.TRUSTED_REPORTER))
                .thenReturn(false);
        when(userBadgeRepository.existsByUserIdAndBadge(1L, Badge.EXPERT_MAPPER))
                .thenReturn(false);

        List<Badge> awarded = gamificationService.evaluateMilestoneBadges(user);

        assertThat(awarded).containsExactlyInAnyOrder(Badge.TRUSTED_REPORTER, Badge.EXPERT_MAPPER);
    }

    @Test
    void evaluateMilestoneBadges_isIdempotent_existingBadgeNotReAwarded() {
        RegisteredUser user = newUser(1L, 0);
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(15L);
        when(userBadgeRepository.existsByUserIdAndBadge(1L, Badge.TRUSTED_REPORTER))
                .thenReturn(true); // already held

        List<Badge> awarded = gamificationService.evaluateMilestoneBadges(user);

        assertThat(awarded).isEmpty();
        verify(userBadgeRepository, never()).save(any(UserBadge.class));
    }

    @Test
    void evaluateMilestoneBadges_belowThresholdAwardsNothing() {
        RegisteredUser user = newUser(1L, 0);
        when(reportRepository.countByCreatedByIdAndStatus(1L, ReportStatus.VERIFIED))
                .thenReturn(9L);

        List<Badge> awarded = gamificationService.evaluateMilestoneBadges(user);

        assertThat(awarded).isEmpty();
        verify(userBadgeRepository, never()).save(any(UserBadge.class));
    }

    // ───────────────────────── helpers ──────────────────────────────────

    private static RegisteredUser newUser(Long id, int initialPoints) {
        RegisteredUser u = new RegisteredUser();
        u.setId(id);
        u.setName("user-" + id);
        u.setEmail("u" + id + "@example.com");
        u.setPoints(initialPoints);
        return u;
    }

    private static Report stubReport(RegisteredUser author) {
        Report r = new Report(author, GeoUtils.point4326(41.0, 29.0), "test report",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReflectionTestUtils.setField(r, "reportId", 100L);
        return r;
    }
}
