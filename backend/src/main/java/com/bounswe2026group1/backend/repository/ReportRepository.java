package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByCreatedById(Long userId);

    List<Report> findByStatus(ReportStatus status);

    /**
     * Returns reports whose tag is in {@code tags} and whose status is in {@code statuses}.
     * Pass the statuses you want (e.g. PENDING, VERIFIED) — not the ones to exclude.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE r.tag IN :tags
            AND r.status IN :statuses
            """)
    List<Report> findByTagInAndStatusIn(
            @Param("tags") Collection<Tag> tags,
            @Param("statuses") Collection<ReportStatus> statuses);

    /**
     * Returns reports within the bounding box whose status is in {@code statuses}.
     * Pass the statuses you want (e.g. PENDING, VERIFIED) — not the ones to exclude.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE r.status IN :statuses
            AND r.location.latitude  BETWEEN :minLat AND :maxLat
            AND r.location.longitude BETWEEN :minLon AND :maxLon
            """)
    List<Report> findReportsInBoundingBoxWithStatuses(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("statuses") Collection<ReportStatus> statuses);

    /**
     * Reports that have been in FIXED state long enough for the cleanup job to delete them.
     * Used by ReportLifecycleScheduler.
     */
    List<Report> findByStatusAndFixedAtBefore(ReportStatus status, Instant cutoff);
}
