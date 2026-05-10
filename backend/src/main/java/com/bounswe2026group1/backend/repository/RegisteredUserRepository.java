package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisteredUserRepository extends JpaRepository<RegisteredUser, Long>,
        JpaSpecificationExecutor<RegisteredUser> {

    boolean existsByEmail(String email);
    Optional<RegisteredUser> findByEmail(String email);

    // Filtering for admin user list
    Page<RegisteredUser> findByStatusAndRole(UserStatus status, UserRole role, Pageable pageable);
    Page<RegisteredUser> findByStatus(UserStatus status, Pageable pageable);
    Page<RegisteredUser> findByRole(UserRole role, Pageable pageable);

    // Used to enforce the "at least one admin" rule
    long countByRole(UserRole role);
    long countByStatus(UserStatus status);

    // ───── Leaderboard queries ─────────────────────────────────────────────

    /** Public leaderboard view — opt-outs and non-active accounts (banned,
     *  anonymised post-deletion) are excluded. Ordered by points descending,
     *  then id ascending so ties resolve deterministically and stably across
     *  recomputes. Caller passes a Pageable with the desired page size
     *  (top 100 in production). */
    @Query("""
            select u from RegisteredUser u
            where u.leaderboardHidden = false
              and u.status = com.bounswe2026group1.backend.model.UserStatus.ACTIVE
            order by u.points desc, u.id asc
            """)
    List<RegisteredUser> findLeaderboardPage(Pageable pageable);

    /** Counts users strictly above a given point total (same opt-out and
     *  status filters as the leaderboard view), so a caller's rank is
     *  {@code count + 1}. We tolerate ties by counting strictly-above-only;
     *  the tie-break in display ordering is by id, but for "your rank" the
     *  user is happy to share the rank with anyone on the same point total. */
    @Query("""
            select count(u) from RegisteredUser u
            where u.leaderboardHidden = false
              and u.status = com.bounswe2026group1.backend.model.UserStatus.ACTIVE
              and u.points > :points
            """)
    long countAboveForRank(@Param("points") int points);

    // User search (#306 / #501): case-insensitive substring match across name OR email, regular users only.
    @Query("SELECT u FROM RegisteredUser u WHERE u.role = com.bounswe2026group1.backend.model.UserRole.USER " +
           "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<RegisteredUser> searchRegularUsersByNameOrEmail(@Param("q") String q, Pageable pageable);
}
