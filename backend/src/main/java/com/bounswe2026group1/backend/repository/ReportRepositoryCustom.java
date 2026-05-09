package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportRepositoryCustom {

    /**
     * PostGIS geography distance: filters within {@code radiusInKm} (converted to meters for {@code ST_DWithin})
     * and sorts strictly by ascending {@code ST_Distance} (closest first). Pagination applies after this order.
     */
    Page<Report> findFeedWithinRadius(
            ReportType reportType,
            ReportEnvironment environment,
            double latitude,
            double longitude,
            double radiusInKm,
            Pageable pageable);

    /**
     * Default feed ordering: newest first by {@link com.bounswe2026group1.backend.model.Report#getPublishDate()}.
     */
    Page<Report> findFeedRecent(
            ReportType reportType,
            ReportEnvironment environment,
            Pageable pageable);
}
