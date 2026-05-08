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
}
