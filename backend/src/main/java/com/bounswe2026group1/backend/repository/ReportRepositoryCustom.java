package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface ReportRepositoryCustom {

    int FEED_RADIUS_METERS = 1000;

    /**
     * PostGIS geography distance: filters within radius and sorts by ascending {@code ST_Distance} (meters).
     */
    Page<Report> findFeedWithinRadius(
            Collection<Long> categoryIds,
            ReportEnvironment environment,
            double latitude,
            double longitude,
            Pageable pageable);

    /**
     * Default feed ordering: newest first by {@link com.bounswe2026group1.backend.model.Report#getPublishDate()}.
     */
    Page<Report> findFeedRecent(
            Collection<Long> categoryIds,
            ReportEnvironment environment,
            Pageable pageable);
}
