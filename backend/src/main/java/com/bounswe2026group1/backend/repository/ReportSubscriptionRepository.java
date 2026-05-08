package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.ReportSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportSubscriptionRepository extends JpaRepository<ReportSubscription, Long> {

    Optional<ReportSubscription> findByUserIdAndReportReportId(Long userId, Long reportId);

    boolean existsByUserIdAndReportReportId(Long userId, Long reportId);

    void deleteByUserIdAndReportReportId(Long userId, Long reportId);

    /** Returns the user ids of all subscribers of a report.
     *  Used by the notification audience fan-out, so we project ids only
     *  and avoid hydrating full subscription / user entities. */
    @Query("select s.user.id from ReportSubscription s where s.report.reportId = :reportId")
    List<Long> findSubscriberUserIdsByReportId(@Param("reportId") Long reportId);
}
