package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report>, ReportRepositoryCustom {

    List<Report> findByCreatedById(Long userId);
    long countByStatus(ReportStatus status);

    long countByCreatedById(Long userId);

    /** Used by the milestone badge engine (Trusted Reporter / Expert Mapper). */
    long countByCreatedByIdAndStatus(Long userId, ReportStatus status);

    /** Batch variant of {@link #countByCreatedById}: one query, grouped by user.
     *  Returns rows of {@code [userId, count]}; absent users mean zero. */
    @Query("""
            SELECT r.createdBy.id, COUNT(r) FROM Report r
            WHERE r.createdBy.id IN :userIds
            GROUP BY r.createdBy.id
            """)
    List<Object[]> countByCreatedByIdIn(@Param("userIds") Collection<Long> userIds);

    List<Report> findByStatus(ReportStatus status);

    /**
     * Routing-only query: restricted to {@code environment = OUTDOOR} because the
     * route geometry comes from ORS foot-walking on outdoor pedestrian network.
     * Indoor reports (broken elevators, heavy doors, etc.) stay in the report
     * system as informational data; they don't generate avoid polygons because
     * routing can't navigate to or around them.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE r.status IN :statuses
            AND r.reportType = :type
            AND r.environment = com.bounswe2026group1.backend.model.ReportEnvironment.OUTDOOR
            """)
    List<Report> findByTypeAndStatusIn(
            @Param("type") ReportType type,
            @Param("statuses") Collection<ReportStatus> statuses);

    /**
     * Routing-only native query.
     *
     * <p>Caller MUST pass status names (e.g. {@code ReportStatus.VERIFIED.name()})
     * and the type name, not enum values. Hibernate binds {@code Collection<Enum>}
     * as ordinals (smallint) in native queries, which collides with the varchar
     * columns produced by {@code @Enumerated(STRING)}.
     *
     * <p>Filtered to {@code environment = 'OUTDOOR'} because the route geometry
     * comes from ORS foot-walking on the outdoor pedestrian network. Indoor
     * reports stay in the report system as informational data.
     */
    @Query(value = """
            SELECT r.* FROM reports r
            WHERE r.status IN (:statuses)
            AND r.report_type = :type
            AND r.environment = 'OUTDOOR'
            AND ST_Within(r.location, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
            """,
            nativeQuery = true)
    List<Report> findByTypeInBoundingBoxWithStatuses(
            @Param("type") String type,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("statuses") Collection<String> statuses);

    /** Routing-only native query. See sibling method above for binding + environment caveats. */
    @Query(value = """
            SELECT r.* FROM reports r
            WHERE r.status IN (:statuses)
            AND r.environment = 'OUTDOOR'
            AND ST_Within(r.location, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
            """,
            nativeQuery = true)
    List<Report> findReportsInBoundingBoxWithStatuses(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("statuses") Collection<String> statuses);

    /**
     * Reports that have been in FIXED state long enough for the cleanup job to delete them.
     * Used by ReportLifecycleScheduler.
     */
    List<Report> findByStatusAndFixedAtBefore(ReportStatus status, Instant cutoff);
}
