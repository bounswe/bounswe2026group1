package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.FixRequestVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixRequestVoteRepository extends JpaRepository<FixRequestVote, Long> {

    Optional<FixRequestVote> findByUserIdAndFixRequestId(Long userId, Long fixRequestId);

    /**
     * Batch-loads a user's votes for a set of fix requests so the report listing endpoint
     * can hydrate {@code activeFixRequest.userVote} without an N+1 query.
     * Returns tuples of {@code [fixRequestId, VoteType]}.
     */
    @Query("""
            select frv.fixRequest.id, frv.voteType
            from FixRequestVote frv
            where frv.user.id = :userId and frv.fixRequest.id in :fixRequestIds
            """)
    List<Object[]> findVotesByUserIdAndFixRequestIds(@Param("userId") Long userId,
                                                     @Param("fixRequestIds") Collection<Long> fixRequestIds);

    /** Returns {@code [userId, VoteType]} tuples for everyone who voted on a fix request.
     *  Used by GamificationService at FIXED-transition time to award/deduct points to
     *  voters based on whether they aligned with the resolved outcome. */
    @Query("""
            select frv.user.id, frv.voteType
            from FixRequestVote frv
            where frv.fixRequest.id = :fixRequestId
            """)
    List<Object[]> findVoteTuplesByFixRequestId(@Param("fixRequestId") Long fixRequestId);
}
