package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportRepositoryImpl implements ReportRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Report> findFeedWithinRadius(
            ReportType reportType,
            ReportEnvironment environment,
            double latitude,
            double longitude,
            double radiusInKm,
            Pageable pageable) {

        // Use feed-specific parameter names — short names like :lat/:lon/:reportType can clash with
        // Hibernate internals or repeat-binding quirks in native SQL; this keeps proximity + filters reliable.
        StringBuilder fromWhere = new StringBuilder("""
                FROM reports r
                WHERE ST_DWithin(
                    r.location::geography,
                    ST_SetSRID(ST_MakePoint(:feedLon, :feedLat), 4326)::geography,
                    :feedRadiusMeters
                )
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("feedLat", latitude);
        params.put("feedLon", longitude);
        params.put("feedRadiusMeters", radiusInKm * 1000.0);

        appendNativeReportTypeFilter(fromWhere, params, reportType);
        appendNativeEnvironmentFilter(fromWhere, params, environment);

        String orderBy = """
                ORDER BY ST_Distance(
                    r.location::geography,
                    ST_SetSRID(ST_MakePoint(:feedLon, :feedLat), 4326)::geography
                ) ASC
                """;

        String countSql = "SELECT count(*) " + fromWhere;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindAll(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "SELECT r.* " + fromWhere + orderBy + " LIMIT :feedLimit OFFSET :feedOffset";
        Query dataQuery = entityManager.createNativeQuery(dataSql, Report.class);
        bindAll(dataQuery, params);
        dataQuery.setParameter("feedLimit", pageable.getPageSize());
        dataQuery.setParameter("feedOffset", (int) pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Report> content = dataQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<Report> findFeedRecent(
            ReportType reportType,
            ReportEnvironment environment,
            Pageable pageable) {

        StringBuilder base = new StringBuilder("FROM Report r WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendJpqlReportTypeFilter(base, params, reportType);
        appendJpqlEnvironmentFilter(base, params, environment);

        String countHql = "SELECT count(r) " + base;
        TypedQuery<Long> countQuery = entityManager.createQuery(countHql, Long.class);
        bindAllTyped(countQuery, params);
        long total = countQuery.getSingleResult();

        String dataHql = "SELECT r " + base + " ORDER BY r.publishDate DESC";
        TypedQuery<Report> dataQuery = entityManager.createQuery(dataHql, Report.class);
        bindAllTyped(dataQuery, params);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Report> content = dataQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private static void appendNativeReportTypeFilter(StringBuilder sql, Map<String, Object> params, ReportType reportType) {
        if (reportType == null) {
            return;
        }
        sql.append(" AND r.report_type = :feedReportType");
        params.put("feedReportType", reportType.name());
    }

    private static void appendNativeEnvironmentFilter(StringBuilder sql, Map<String, Object> params, ReportEnvironment environment) {
        if (environment == null) {
            return;
        }
        sql.append(" AND r.environment = :feedEnvironment");
        params.put("feedEnvironment", environment.name());
    }

    private static void appendJpqlReportTypeFilter(StringBuilder jpql, Map<String, Object> params, ReportType reportType) {
        if (reportType == null) {
            return;
        }
        jpql.append(" AND r.reportType = :reportType");
        params.put("reportType", reportType);
    }

    private static void appendJpqlEnvironmentFilter(StringBuilder jpql, Map<String, Object> params, ReportEnvironment environment) {
        if (environment == null) {
            return;
        }
        jpql.append(" AND r.environment = :feedEnv");
        params.put("feedEnv", environment);
    }

    private static void bindAll(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
    }

    private static void bindAllTyped(TypedQuery<?> query, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
    }
}
