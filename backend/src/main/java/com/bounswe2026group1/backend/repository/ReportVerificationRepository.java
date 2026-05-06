package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportVerificationRepository extends JpaRepository<ReportVerification, Long>,
        JpaSpecificationExecutor<ReportVerification> {
    Optional<ReportVerification> findByUserIdAndReportReportId(Long userId, Long reportId);

    /**
     * Batch-loads a user's votes for a set of reports.
     * Returns tuples of {@code [reportId, VoteType]} to avoid fetching full Report entities.
     */
    @Query("""
            select rv.report.reportId, rv.voteType
            from ReportVerification rv
            where rv.user.id = :userId and rv.report.reportId in :reportIds
            """)
    List<Object[]> findVotesByUserIdAndReportIds(@Param("userId") Long userId,
                                                 @Param("reportIds") Collection<Long> reportIds);

    /** Returns the user ids of everyone who has voted (agree or disagree) on a report.
     *  Used by the notification audience fan-out, so we project ids only. */
    @Query("select distinct rv.user.id from ReportVerification rv where rv.report.reportId = :reportId")
    List<Long> findVoterUserIdsByReportId(@Param("reportId") Long reportId);
}
