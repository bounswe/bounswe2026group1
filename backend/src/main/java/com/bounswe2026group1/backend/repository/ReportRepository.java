package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByCreatedById(Long userId);

    long countByCreatedById(Long userId);

    /** Batch variant of {@link #countByCreatedById}: one query, grouped by user.
     *  Returns rows of {@code [userId, count]}; absent users mean zero. */
    @Query("""
            SELECT r.createdBy.id, COUNT(r) FROM Report r
            WHERE r.createdBy.id IN :userIds
            GROUP BY r.createdBy.id
            """)
    List<Object[]> countByCreatedByIdIn(@Param("userIds") Collection<Long> userIds);

    List<Report> findByStatus(ReportStatus status);

    @Query("""
            SELECT r FROM Report r
            WHERE r.status IN :statuses
            AND r.reportType = :type
            """)
    List<Report> findByTypeAndStatusIn(
            @Param("type") ReportType type,
            @Param("statuses") Collection<ReportStatus> statuses);

    @Query("""
            SELECT r FROM Report r
            WHERE r.status IN :statuses
            AND r.reportType = :type
            AND r.location.latitude  BETWEEN :minLat AND :maxLat
            AND r.location.longitude BETWEEN :minLon AND :maxLon
            """)
    List<Report> findByTypeInBoundingBoxWithStatuses(
            @Param("type") ReportType type,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("statuses") Collection<ReportStatus> statuses);

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
}
