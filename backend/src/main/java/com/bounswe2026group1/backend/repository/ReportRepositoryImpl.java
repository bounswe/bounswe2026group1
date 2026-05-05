package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportRepositoryImpl implements ReportRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Report> findFeedWithinRadius(
            Collection<Long> categoryIds,
            ReportEnvironment environment,
            double latitude,
            double longitude,
            Pageable pageable) {

        StringBuilder fromWhere = new StringBuilder("""
                FROM reports r
                WHERE ST_DWithin(
                    r.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :radiusMeters
                )
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("lat", latitude);
        params.put("lon", longitude);
        params.put("radiusMeters", FEED_RADIUS_METERS);

        appendNativeCategoryFilter(fromWhere, params, categoryIds);
        appendNativeEnvironmentFilter(fromWhere, params, environment);

        String orderBy = """
                ORDER BY ST_Distance(
                    r.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) ASC
                """;

        String countSql = "SELECT count(*) " + fromWhere;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindAll(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "SELECT r.* " + fromWhere + orderBy + " LIMIT :limit OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql, Report.class);
        bindAll(dataQuery, params);
        dataQuery.setParameter("limit", pageable.getPageSize());
        dataQuery.setParameter("offset", (int) pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Report> content = dataQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<Report> findFeedRecent(
            Collection<Long> categoryIds,
            ReportEnvironment environment,
            Pageable pageable) {

        StringBuilder base = new StringBuilder("FROM Report r WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendJpqlCategoryFilter(base, params, categoryIds);
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

    private static void appendNativeCategoryFilter(StringBuilder sql, Map<String, Object> params, Collection<Long> categoryIds) {
        if (categoryIds == null) {
            return;
        }
        if (categoryIds.isEmpty()) {
            sql.append(" AND 1=0");
            return;
        }
        sql.append(" AND r.category_id IN (:catIds)");
        params.put("catIds", categoryIds);
    }

    private static void appendNativeEnvironmentFilter(StringBuilder sql, Map<String, Object> params, ReportEnvironment environment) {
        if (environment == null) {
            return;
        }
        sql.append(" AND r.environment = :environment");
        params.put("environment", environment.name());
    }

    private static void appendJpqlCategoryFilter(StringBuilder jpql, Map<String, Object> params, Collection<Long> categoryIds) {
        if (categoryIds == null) {
            return;
        }
        if (categoryIds.isEmpty()) {
            jpql.append(" AND 1=0");
            return;
        }
        jpql.append(" AND r.category.id IN (:catIds)");
        params.put("catIds", categoryIds);
    }

    private static void appendJpqlEnvironmentFilter(StringBuilder jpql, Map<String, Object> params, ReportEnvironment environment) {
        if (environment == null) {
            return;
        }
        jpql.append(" AND r.environment = :environment");
        params.put("environment", environment);
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
