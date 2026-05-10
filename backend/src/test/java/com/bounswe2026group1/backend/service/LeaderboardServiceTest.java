package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserBadge;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.UserBadgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock private RegisteredUserRepository userRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        // Smaller cutoffs in unit tests so we don't have to fabricate
        // 100 users to exercise the full pipeline.
        ReflectionTestUtils.setField(leaderboardService, "leaderboardSize", 5);
        ReflectionTestUtils.setField(leaderboardService, "topBadgeRank", 3);
    }

    // ─────────────────────────── getTopN ────────────────────────────────

    @Test
    void getTopN_assignsRanksOneBased_inOrder() {
        RegisteredUser u1 = newUser(1L, "Alice", 100);
        RegisteredUser u2 = newUser(2L, "Bob", 80);
        RegisteredUser u3 = newUser(3L, "Carol", 50);
        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(u1, u2, u3));

        List<LeaderboardEntry> result = leaderboardService.getTopN();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).points()).isEqualTo(100);
        assertThat(result.get(2).rank()).isEqualTo(3);
        assertThat(result.get(2).userId()).isEqualTo(3L);
    }

    @Test
    void getTopN_emptyRepoReturnsEmptyList() {
        when(userRepository.findLeaderboardPage(any(Pageable.class))).thenReturn(List.of());

        assertThat(leaderboardService.getTopN()).isEmpty();
    }

    @Test
    void getTopN_tiedScoresShareRank_andSkipGap() {
        // Olympic-style: two users at 100 share rank 1, the next user at 80
        // gets rank 3 (rank 2 is skipped). Keeps getTopN consistent with
        // getRankFor's count-above arithmetic, so a "your rank" banner
        // never disagrees with the user's row in the list.
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 100);
        RegisteredUser carol = newUser(3L, "Carol", 80);
        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob, carol));

        List<LeaderboardEntry> result = leaderboardService.getTopN();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(1).rank()).isEqualTo(1); // tied with Alice
        assertThat(result.get(2).rank()).isEqualTo(3); // gap after the tie
    }

    // ──────────────────────────── getRankFor ────────────────────────────

    @Test
    void getRankFor_activeUserReturnsCountPlusOne() {
        RegisteredUser u = newUser(7L, "Dora", 60);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(userRepository.countAboveForRank(60)).thenReturn(4L);

        Optional<RankInfo> info = leaderboardService.getRankFor(7L);

        assertThat(info).isPresent();
        assertThat(info.get().rank()).isEqualTo(5);
        assertThat(info.get().points()).isEqualTo(60);
    }

    @Test
    void getRankFor_optedOutReturnsEmpty() {
        RegisteredUser u = newUser(7L, "Dora", 60);
        u.setLeaderboardHidden(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThat(leaderboardService.getRankFor(7L)).isEmpty();
    }

    @Test
    void getRankFor_bannedUserReturnsEmpty() {
        RegisteredUser u = newUser(7L, "Dora", 60);
        u.setStatus(UserStatus.BANNED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThat(leaderboardService.getRankFor(7L)).isEmpty();
    }

    @Test
    void getRankFor_unknownUserReturnsEmpty() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(leaderboardService.getRankFor(99L)).isEmpty();
    }

    @Test
    void getRankFor_nullIdReturnsEmpty() {
        assertThat(leaderboardService.getRankFor(null)).isEmpty();
    }

    // ────────────────────── onPointsChanged (top-10 churn) ──────────────

    @Test
    void onPointsChanged_notifiesNewcomerOfTop10Badge() {
        // Carol just rose into the cutoff — she should both receive the badge
        // row AND be notified.
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 90);
        RegisteredUser carol = newUser(3L, "Carol", 80);

        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob, carol));
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(carol));

        leaderboardService.onPointsChanged(3L);

        verify(notificationService).notifyBadgeAwarded(carol, Badge.TOP_10);
    }

    @Test
    void onPointsChanged_doesNotNotifyOnRevoke() {
        // Falling out of the top-10 is a silent revoke — no notification.
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 90);
        RegisteredUser carol = newUser(3L, "Carol", 80);

        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob, carol));
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L, 3L, 4L)); // Dora (4) needs revoking

        leaderboardService.onPointsChanged(4L);

        verify(notificationService, never()).notifyBadgeAwarded(any(), any());
    }

    @Test
    void onPointsChanged_awardsBadgeToNewcomer() {
        // Top-3 cutoff: Alice & Bob were in, Carol just rose into the cutoff.
        // Currently held by: Alice, Bob.
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 90);
        RegisteredUser carol = newUser(3L, "Carol", 80);

        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob, carol));
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(carol));

        leaderboardService.onPointsChanged(3L);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(3L);
        assertThat(captor.getValue().getBadge()).isEqualTo(Badge.TOP_10);
        verify(userBadgeRepository, never()).deleteByUserIdAndBadge(any(), any());
    }

    @Test
    void onPointsChanged_revokesBadgeFromUserWhoFellOut() {
        // Top-3 cutoff: now Alice, Bob, Carol. Previously also Dora.
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 90);
        RegisteredUser carol = newUser(3L, "Carol", 80);

        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob, carol));
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L, 3L, 4L)); // Dora (4) needs revoking

        leaderboardService.onPointsChanged(4L);

        verify(userBadgeRepository).deleteByUserIdAndBadge(eq(4L), eq(Badge.TOP_10));
        verify(userBadgeRepository, never()).save(any());
    }

    @Test
    void onPointsChanged_noChurn_noWrites() {
        RegisteredUser alice = newUser(1L, "Alice", 100);
        RegisteredUser bob = newUser(2L, "Bob", 90);

        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of(alice, bob));
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L));

        leaderboardService.onPointsChanged(1L);

        verify(userBadgeRepository, never()).save(any());
        verify(userBadgeRepository, never()).deleteByUserIdAndBadge(any(), any());
    }

    @Test
    void onPointsChanged_emptyLeaderboardRevokesAllExistingHolders() {
        // Defensive edge case: every previously-top-10 user has somehow
        // been excluded (opt-out wave or admin ban). All TOP_10 badges revoke.
        when(userRepository.findLeaderboardPage(any(Pageable.class)))
                .thenReturn(List.of());
        when(userBadgeRepository.findUserIdsByBadge(Badge.TOP_10))
                .thenReturn(List.of(1L, 2L));

        leaderboardService.onPointsChanged(1L);

        verify(userBadgeRepository).deleteByUserIdAndBadge(eq(1L), eq(Badge.TOP_10));
        verify(userBadgeRepository).deleteByUserIdAndBadge(eq(2L), eq(Badge.TOP_10));
        verify(userBadgeRepository, never()).save(any());
    }

    // ───────────────────────── helpers ──────────────────────────────────

    private static RegisteredUser newUser(Long id, String name, int points) {
        RegisteredUser u = new RegisteredUser();
        u.setId(id);
        u.setName(name);
        u.setEmail(name.toLowerCase() + "@example.com");
        u.setPoints(points);
        u.setStatus(UserStatus.ACTIVE);
        u.setLeaderboardHidden(false);
        return u;
    }
}
