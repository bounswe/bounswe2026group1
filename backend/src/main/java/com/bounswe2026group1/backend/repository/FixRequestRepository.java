package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.FixRequest;
import com.bounswe2026group1.backend.model.FixRequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixRequestRepository extends JpaRepository<FixRequest, Long> {

    Optional<FixRequest> findFirstByReportReportIdAndState(Long reportId, FixRequestState state);

    List<FixRequest> findByStateAndCreatedAtBefore(FixRequestState state, Instant cutoff);

    /**
     * Batch-loads the active OPEN fix request id for each report id, so the report listing
     * endpoint can hydrate {@code activeFixRequest} without an N+1 query.
     * Returns tuples of {@code [reportId, fixRequestId]}.
     */
    @Query("""
            select fr.report.reportId, fr.id
            from FixRequest fr
            where fr.state = com.bounswe2026group1.backend.model.FixRequestState.OPEN
              and fr.report.reportId in :reportIds
            """)
    List<Object[]> findOpenFixRequestIdsByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
