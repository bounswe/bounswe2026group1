package com.bounswe2026group1.backend.repository.spec;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ReportSpecifications {

    private ReportSpecifications() {
    }

    /**
     * Optional filters for the chronological feed. Type matches either the leaf category's
     * {@link ReportCategory#getType()} or, when null on the leaf, the parent category's type
     * (one-level inheritance; sufficient for demo trees).
     */
    public static Specification<Report> feedFilters(
            Long categoryId,
            ReportType type,
            ReportEnvironment environment,
            Long userId) {

        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), userId));
            }
            if (environment != null) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            boolean categoryJoinNeeded = categoryId != null || type != null;
            if (categoryJoinNeeded) {
                Join<Report, ReportCategory> cat = root.join("category", JoinType.INNER);
                Join<ReportCategory, ReportCategory> parent = cat.join("parent", JoinType.LEFT);
                if (categoryId != null) {
                    predicates.add(cb.equal(cat.get("id"), categoryId));
                }
                if (type != null) {
                    Predicate onLeaf = cb.equal(cat.get("type"), type);
                    Predicate inheritedFromParent = cb.and(
                            cb.isNull(cat.get("type")),
                            cb.equal(parent.get("type"), type));
                    predicates.add(cb.or(onLeaf, inheritedFromParent));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
