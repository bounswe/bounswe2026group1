package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.dto.FeedSort;
import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportRepositoryImpl implements ReportRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Report> findFeedWithinRadius(ReportFeedQuery filter, Pageable pageable) {

        // Single ST_MakePoint via CTE so :feedLon/:feedLat appear only once. Some JDBC/Hibernate
        // stacks mishandle duplicate native named parameters; that surfaced as 500 when combining
        // proximity + enum filters.
        String withClause = """
                WITH ref AS (
                    SELECT ST_SetSRID(ST_MakePoint(:feedLon, :feedLat), 4326)::geography AS g
                )
                """;

        StringBuilder fromWhere = new StringBuilder("""
                FROM reports r, ref
                WHERE ST_DWithin(
                    r.location::geography,
                    ref.g,
                    :feedRadiusMeters
                )
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("feedLat", filter.getLatitude());
        params.put("feedLon", filter.getLongitude());
        params.put("feedRadiusMeters",
                (filter.getRadiusInKm() != null ? filter.getRadiusInKm() : 1.0) * 1000.0);

        appendNativeFilters(fromWhere, params, filter);

        String orderBy = nativeOrderBy(resolveSort(filter, /*hasCoords=*/ true));

        String countSql = withClause + "SELECT count(*) " + fromWhere;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindAll(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = withClause + "SELECT r.* " + fromWhere + orderBy;
        Query dataQuery = entityManager.createNativeQuery(dataSql, Report.class);
        bindAll(dataQuery, params);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Report> content = dataQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<Report> findFeedRecent(ReportFeedQuery filter, Pageable pageable) {

        StringBuilder base = new StringBuilder("FROM Report r WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendJpqlFilters(base, params, filter);

        String countHql = "SELECT count(r) " + base;
        TypedQuery<Long> countQuery = entityManager.createQuery(countHql, Long.class);
        bindAllTyped(countQuery, params);
        long total = countQuery.getSingleResult();

        String dataHql = "SELECT r " + base + jpqlOrderBy(resolveSort(filter, /*hasCoords=*/ false));
        TypedQuery<Report> dataQuery = entityManager.createQuery(dataHql, Report.class);
        bindAllTyped(dataQuery, params);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Report> content = dataQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // ----- shared filter assembly --------------------------------------------------------------

    private static void appendNativeFilters(StringBuilder sql, Map<String, Object> params,
                                            ReportFeedQuery filter) {
        ReportType reportType = filter.getReportType();
        if (reportType != null) {
            sql.append(" AND r.report_type = :feedReportType");
            params.put("feedReportType", reportType.name());
        }
        ReportEnvironment environment = filter.getEnvironment();
        if (environment != null) {
            sql.append(" AND r.environment = :feedEnvironment");
            params.put("feedEnvironment", environment.name());
        }
        List<ReportStatus> statuses = nonEmpty(filter.getStatus());
        if (statuses != null) {
            sql.append(" AND r.status IN (:feedStatuses)");
            params.put("feedStatuses", statuses.stream().map(Enum::name).toList());
        }
        if (filter.getAuthorId() != null) {
            sql.append(" AND r.user_id = :feedAuthorId");
            params.put("feedAuthorId", filter.getAuthorId());
        }
        if (filter.getPublishedAfter() != null) {
            sql.append(" AND r.publish_date >= :feedPublishedAfter");
            params.put("feedPublishedAfter", filter.getPublishedAfter());
        }
        if (filter.getPublishedBefore() != null) {
            sql.append(" AND r.publish_date <= :feedPublishedBefore");
            params.put("feedPublishedBefore", filter.getPublishedBefore());
        }
        String q = normalizeQ(filter.getQ());
        if (q != null) {
            sql.append(" AND r.description ILIKE :feedQ");
            params.put("feedQ", "%" + q + "%");
        }
        if (filter.getMinAgrees() != null) {
            sql.append(" AND r.agrees >= :feedMinAgrees");
            params.put("feedMinAgrees", filter.getMinAgrees());
        }
        if (filter.getMinDisagrees() != null) {
            sql.append(" AND r.disagrees >= :feedMinDisagrees");
            params.put("feedMinDisagrees", filter.getMinDisagrees());
        }
        if (filter.getMinNetScore() != null) {
            sql.append(" AND (r.agrees - r.disagrees) >= :feedMinNetScore");
            params.put("feedMinNetScore", filter.getMinNetScore());
        }
        // Merged object/pair filter: object types covered by an `objectIssue` pair are
        // narrowed to that pair, uncovered object types match any issue (or no issue). All
        // clauses are OR'd inside a single EXISTS so picking RAMP+MISSING alongside a plain
        // DOOR chip yields "RAMP-with-MISSING OR any-DOOR" — not their intersection.
        List<ObjectType> objectTypes = nonEmpty(filter.getObjectType());
        List<String[]> pairs = parseObjectIssuePairs(filter.getObjectIssue());
        appendNativeObjectFilterClause(sql, params, objectTypes, pairs);

        // Legacy flat `issueType` filter — kept as an independent EXISTS for API consumers
        // that only want "any object on the report has one of these issues".
        List<IssueType> issueTypes = nonEmpty(filter.getIssueType());
        if (issueTypes != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM report_objects ro2")
                    .append(" JOIN report_object_issues roi ON roi.report_object_id = ro2.id")
                    .append(" WHERE ro2.report_id = r.report_id")
                    .append(" AND roi.issue_type IN (:feedIssueTypes))");
            params.put("feedIssueTypes", issueTypes.stream().map(Enum::name).toList());
        }
    }

    private static void appendNativeObjectFilterClause(StringBuilder sql,
                                                       Map<String, Object> params,
                                                       List<ObjectType> objectTypes,
                                                       List<String[]> pairs) {
        if ((objectTypes == null || objectTypes.isEmpty()) && pairs.isEmpty()) return;

        java.util.Set<String> coveredTypes = new java.util.HashSet<>();
        for (String[] p : pairs) coveredTypes.add(p[0]);

        StringBuilder or = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            String tParam = "feedOi" + i + "_t";
            String iParam = "feedOi" + i + "_i";
            if (or.length() > 0) or.append(" OR ");
            or.append("(ro3.object_type = :").append(tParam)
              .append(" AND roi3.issue_type = :").append(iParam).append(")");
            params.put(tParam, pairs.get(i)[0]);
            params.put(iParam, pairs.get(i)[1]);
        }
        if (objectTypes != null) {
            int k = 0;
            for (ObjectType ot : objectTypes) {
                if (coveredTypes.contains(ot.name())) continue;
                String tParam = "feedOt" + k + "_any";
                if (or.length() > 0) or.append(" OR ");
                or.append("(ro3.object_type = :").append(tParam).append(")");
                params.put(tParam, ot.name());
                k++;
            }
        }
        if (or.length() == 0) return;

        // LEFT JOIN so an uncovered-type clause still matches report_objects that carry no
        // issue rows (a RAMP with no issues should pass an "any RAMP" filter).
        sql.append(" AND EXISTS (SELECT 1 FROM report_objects ro3")
                .append(" LEFT JOIN report_object_issues roi3 ON roi3.report_object_id = ro3.id")
                .append(" WHERE ro3.report_id = r.report_id")
                .append(" AND (").append(or).append("))");
    }

    private static void appendJpqlFilters(StringBuilder jpql, Map<String, Object> params,
                                          ReportFeedQuery filter) {
        ReportType reportType = filter.getReportType();
        if (reportType != null) {
            jpql.append(" AND r.reportType = :reportType");
            params.put("reportType", reportType);
        }
        ReportEnvironment environment = filter.getEnvironment();
        if (environment != null) {
            jpql.append(" AND r.environment = :feedEnv");
            params.put("feedEnv", environment);
        }
        List<ReportStatus> statuses = nonEmpty(filter.getStatus());
        if (statuses != null) {
            jpql.append(" AND r.status IN :feedStatuses");
            params.put("feedStatuses", statuses);
        }
        if (filter.getAuthorId() != null) {
            jpql.append(" AND r.createdBy.id = :feedAuthorId");
            params.put("feedAuthorId", filter.getAuthorId());
        }
        if (filter.getPublishedAfter() != null) {
            jpql.append(" AND r.publishDate >= :feedPublishedAfter");
            params.put("feedPublishedAfter", filter.getPublishedAfter());
        }
        if (filter.getPublishedBefore() != null) {
            jpql.append(" AND r.publishDate <= :feedPublishedBefore");
            params.put("feedPublishedBefore", filter.getPublishedBefore());
        }
        String q = normalizeQ(filter.getQ());
        if (q != null) {
            jpql.append(" AND LOWER(r.description) LIKE LOWER(:feedQ)");
            params.put("feedQ", "%" + q + "%");
        }
        if (filter.getMinAgrees() != null) {
            jpql.append(" AND r.agrees >= :feedMinAgrees");
            params.put("feedMinAgrees", filter.getMinAgrees());
        }
        if (filter.getMinDisagrees() != null) {
            jpql.append(" AND r.disagrees >= :feedMinDisagrees");
            params.put("feedMinDisagrees", filter.getMinDisagrees());
        }
        if (filter.getMinNetScore() != null) {
            jpql.append(" AND (r.agrees - r.disagrees) >= :feedMinNetScore");
            params.put("feedMinNetScore", filter.getMinNetScore());
        }
        // Merged object/pair filter (see appendNativeObjectFilterClause for the rationale).
        List<ObjectType> objectTypes = nonEmpty(filter.getObjectType());
        List<String[]> pairs = parseObjectIssuePairs(filter.getObjectIssue());
        appendJpqlObjectFilterClause(jpql, params, objectTypes, pairs);

        // Legacy flat issueType — independent EXISTS, kept for API consumers.
        List<IssueType> issueTypes = nonEmpty(filter.getIssueType());
        if (issueTypes != null) {
            jpql.append(" AND EXISTS (SELECT 1 FROM ReportObject ro2 JOIN ro2.issues i")
                    .append(" WHERE ro2.report = r AND i IN :feedIssueTypes)");
            params.put("feedIssueTypes", issueTypes);
        }
    }

    private static void appendJpqlObjectFilterClause(StringBuilder jpql,
                                                     Map<String, Object> params,
                                                     List<ObjectType> objectTypes,
                                                     List<String[]> pairs) {
        if ((objectTypes == null || objectTypes.isEmpty()) && pairs.isEmpty()) return;

        java.util.Set<String> coveredTypes = new java.util.HashSet<>();
        for (String[] p : pairs) coveredTypes.add(p[0]);

        StringBuilder or = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            String tParam = "feedOi" + i + "_t";
            String iParam = "feedOi" + i + "_i";
            if (or.length() > 0) or.append(" OR ");
            or.append("(ro3.objectType = :").append(tParam)
              .append(" AND i3 = :").append(iParam).append(")");
            params.put(tParam, ObjectType.valueOf(pairs.get(i)[0]));
            params.put(iParam, IssueType.valueOf(pairs.get(i)[1]));
        }
        if (objectTypes != null) {
            int k = 0;
            for (ObjectType ot : objectTypes) {
                if (coveredTypes.contains(ot.name())) continue;
                String tParam = "feedOt" + k + "_any";
                if (or.length() > 0) or.append(" OR ");
                or.append("(ro3.objectType = :").append(tParam).append(")");
                params.put(tParam, ot);
                k++;
            }
        }
        if (or.length() == 0) return;

        // LEFT JOIN: an uncovered-type clause must still match a report_object that has no
        // associated issues (a RAMP with no issues should pass an "any RAMP" filter).
        jpql.append(" AND EXISTS (SELECT 1 FROM ReportObject ro3 LEFT JOIN ro3.issues i3")
                .append(" WHERE ro3.report = r")
                .append(" AND (").append(or).append("))");
    }

    /**
     * Parse `OBJECT_TYPE:ISSUE_TYPE` entries into [type, issue] string pairs. Entries are
     * pre-validated by the service, so we only defensively skip malformed leftovers here.
     */
    private static List<String[]> parseObjectIssuePairs(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String[]> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            int sep = trimmed.indexOf(':');
            if (sep <= 0 || sep == trimmed.length() - 1) continue;
            out.add(new String[]{ trimmed.substring(0, sep), trimmed.substring(sep + 1) });
        }
        return out;
    }

    // ----- ordering ----------------------------------------------------------------------------

    private static FeedSort resolveSort(ReportFeedQuery filter, boolean hasCoords) {
        FeedSort sort = filter.getSort();
        if (sort != null) {
            return sort;
        }
        return hasCoords ? FeedSort.DISTANCE : FeedSort.NEWEST;
    }

    private static String nativeOrderBy(FeedSort sort) {
        return switch (sort) {
            case DISTANCE -> " ORDER BY ST_Distance(r.location::geography, ref.g) ASC";
            case OLDEST -> " ORDER BY r.publish_date ASC";
            case MOST_AGREED -> " ORDER BY r.agrees DESC, r.publish_date DESC";
            case MOST_VOTED -> " ORDER BY (r.agrees + r.disagrees) DESC, r.publish_date DESC";
            case NEWEST -> " ORDER BY r.publish_date DESC";
        };
    }

    private static String jpqlOrderBy(FeedSort sort) {
        return switch (sort) {
            case OLDEST -> " ORDER BY r.publishDate ASC";
            case MOST_AGREED -> " ORDER BY r.agrees DESC, r.publishDate DESC";
            case MOST_VOTED -> " ORDER BY (r.agrees + r.disagrees) DESC, r.publishDate DESC";
            // DISTANCE only makes sense in the proximity branch; fall through to NEWEST as a guard.
            case DISTANCE, NEWEST -> " ORDER BY r.publishDate DESC";
        };
    }

    // ----- helpers -----------------------------------------------------------------------------

    private static <T> List<T> nonEmpty(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<T> filtered = new ArrayList<>(list.size());
        for (T item : list) {
            if (item != null) {
                filtered.add(item);
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    private static String normalizeQ(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Cap input length so we don't ship arbitrarily long LIKE patterns to the DB.
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
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
