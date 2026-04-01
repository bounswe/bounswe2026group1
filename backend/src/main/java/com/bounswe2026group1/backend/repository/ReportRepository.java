package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByCreatedById(Long userId);
    List<Report> findByStatus(ReportStatus status);

    List<Report> findByTagInAndStatusNot(Collection<Tag> tags, ReportStatus status);

    @Query("""
            SELECT r FROM Report r
            WHERE r.status <> :excluded
            AND r.location.latitude BETWEEN :minLat AND :maxLat
            AND r.location.longitude BETWEEN :minLon AND :maxLon
            """)
    List<Report> findActiveReportsInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("excluded") ReportStatus excluded);
}
