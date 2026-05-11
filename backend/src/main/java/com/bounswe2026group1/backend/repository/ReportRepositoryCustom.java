package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportRepositoryCustom {

    /**
     * PostGIS geography distance: filters within {@code filter.radiusInKm} (converted to meters
     * for {@code ST_DWithin}) plus any other supplied filters. Sort order is dictated by
     * {@code filter.sort}; defaults to ascending {@code ST_Distance} (closest first) when null.
     * Requires {@code filter.latitude} and {@code filter.longitude} to be non-null.
     */
    Page<Report> findFeedWithinRadius(ReportFeedQuery filter, Pageable pageable);

    /**
     * Non-proximity feed. Applies all supplied filters and orders by {@code filter.sort}
     * (default: publish date descending — newest first).
     */
    Page<Report> findFeedRecent(ReportFeedQuery filter, Pageable pageable);
}
