package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserBadge;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read view + dynamic-badge maintenance for the leaderboard.
 *
 * Reads (top-N, caller rank) are pure JPA queries against the user table.
 * Top-10 badge churn is recomputed eagerly via {@link #onPointsChanged}
 * after every point change, so a user who moves into or out of the top 10
 * sees their badge updated within the same request.
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final RegisteredUserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final NotificationService notificationService;

    @Value("${app.gamification.leaderboard.size:100}")
    private int leaderboardSize;

    @Value("${app.gamification.leaderboard.top-badge-rank:10}")
    private int topBadgeRank;

    /** Top-N (default 100) ranked by points desc, with opt-outs and
     *  non-active accounts filtered out. Olympic-style rank: tied scores
     *  share a rank, the next distinct score skips the gap. Keeps this
     *  view consistent with {@link #getRankFor}, which uses count-above
     *  arithmetic — both report rank 1 for two users at the same top score. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getTopN() {
        List<RegisteredUser> users = userRepository.findLeaderboardPage(
                PageRequest.of(0, leaderboardSize));
        List<LeaderboardEntry> out = new ArrayList<>(users.size());
        int currentRank = 0;
        Integer previousPoints = null;
        for (int i = 0; i < users.size(); i++) {
            RegisteredUser u = users.get(i);
            if (previousPoints == null || u.getPoints() != previousPoints) {
                currentRank = i + 1;
                previousPoints = u.getPoints();
            }
            out.add(new LeaderboardEntry(
                    currentRank,
                    u.getId(),
                    u.getName(),
                    u.getAvatarUrl(),
                    u.getPoints()));
        }
        return out;
    }

    /** Rank for a single user. Returns empty when the user has opted out
     *  or has been deactivated — the UI suppresses the rank banner in
     *  that case rather than showing a misleading number. */
    @Transactional(readOnly = true)
    public Optional<RankInfo> getRankFor(Long userId) {
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId)
                .filter(u -> !u.isLeaderboardHidden())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> new RankInfo(
                        (int) (userRepository.countAboveForRank(u.getPoints()) + 1),
                        u.getPoints()));
    }

    /**
     * Re-evaluates the TOP_10 badge after a user's points changed.
     * Awards the badge to anyone who entered the current top-N-by-rank
     * cutoff (default top 10), revokes from anyone who fell out.
     *
     * <p>Called eagerly from {@link GamificationService#applyDelta} so the
     * dynamic badge tracks rank changes in real time. The work scales with
     * the badge cutoff (10) plus the current TOP_10 holders, so worst case
     * a few rows per call — cheap enough to avoid a scheduled cron.
     */
    @Transactional
    public void onPointsChanged(Long actorUserId) {
        // Snapshot of who SHOULD hold the badge right now.
        List<RegisteredUser> top = userRepository.findLeaderboardPage(
                PageRequest.of(0, topBadgeRank));
        Set<Long> shouldHold = top.stream().map(RegisteredUser::getId)
                .collect(Collectors.toCollection(HashSet::new));

        // Snapshot of who currently holds the badge.
        Set<Long> currentlyHolds = new HashSet<>(
                userBadgeRepository.findUserIdsByBadge(Badge.TOP_10));

        // Award to newcomers. Hydrate the user entities in a single batch
        // so the FK on UserBadge can be set without per-row findById.
        Set<Long> toAward = new HashSet<>(shouldHold);
        toAward.removeAll(currentlyHolds);
        if (!toAward.isEmpty()) {
            Map<Long, RegisteredUser> usersById = userRepository.findAllById(toAward).stream()
                    .collect(Collectors.toMap(RegisteredUser::getId, Function.identity()));
            for (Long id : toAward) {
                RegisteredUser u = usersById.get(id);
                if (u != null) {
                    userBadgeRepository.save(new UserBadge(u, Badge.TOP_10));
                    notificationService.notifyBadgeAwarded(u, Badge.TOP_10);
                }
            }
        }

        // Revoke from users who fell out of the cutoff.
        Set<Long> toRevoke = new HashSet<>(currentlyHolds);
        toRevoke.removeAll(shouldHold);
        for (Long id : toRevoke) {
            userBadgeRepository.deleteByUserIdAndBadge(id, Badge.TOP_10);
        }
    }
}
