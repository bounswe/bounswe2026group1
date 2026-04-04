package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RampReportRepository extends JpaRepository<RampReport, Long> {

    @Query("""
            SELECT r FROM RampReport r
            WHERE r.status IN :statuses
            AND r.location.latitude  BETWEEN :minLat AND :maxLat
            AND r.location.longitude BETWEEN :minLon AND :maxLon
            """)
    List<RampReport> findRampsInBoundingBoxWithStatuses(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("statuses") Collection<ReportStatus> statuses);
}
