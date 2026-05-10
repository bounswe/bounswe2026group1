package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.PointEvent;
import com.bounswe2026group1.backend.model.PointReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointEventRepository extends JpaRepository<PointEvent, Long> {

    Page<PointEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Used by GamificationService to ensure idempotency for vote-cast
     *  awards: at most one VOTE_CAST event per (user, report). */
    boolean existsByUserIdAndRelatedEntityIdAndReason(Long userId,
                                                     Long relatedEntityId,
                                                     PointReason reason);
}
