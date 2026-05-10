package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserId(Long userId);

    Optional<UserBadge> findByUserIdAndBadge(Long userId, Badge badge);

    boolean existsByUserIdAndBadge(Long userId, Badge badge);

    long deleteByUserIdAndBadge(Long userId, Badge badge);

    /** All current holders of a single badge — used to compute the diff
     *  during top-10 recomputation. */
    @Query("select ub.user.id from UserBadge ub where ub.badge = :badge")
    List<Long> findUserIdsByBadge(@Param("badge") Badge badge);

    /** Returns {@code [userId, badge]} pairs for the given user ids — single
     *  query for batch profile rendering and report-author top-badge lookup. */
    @Query("""
            select ub.user.id, ub.badge
            from UserBadge ub
            where ub.user.id in :userIds
            """)
    List<Object[]> findBadgesByUserIds(@Param("userIds") Collection<Long> userIds);
}
