package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ReportSpecifications {

    private ReportSpecifications() {}

    /**
     * Dynamic AND filters. Omit a collection by passing {@code null} to skip that predicate.
     * Empty {@code categoryIds} / {@code typeCategoryIds} means no category matches — caller should short-circuit to an empty page.
     */
    public static Specification<Report> feed(
            Collection<Long> categorySubtreeIds,
            Collection<Long> typeCategoryIds,
            ReportEnvironment environment,
            Long createdByUserId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Report, ReportCategory> category = root.join("category", JoinType.INNER);

            if (categorySubtreeIds != null) {
                predicates.add(category.get("id").in(categorySubtreeIds));
            }
            if (typeCategoryIds != null) {
                predicates.add(category.get("id").in(typeCategoryIds));
            }
            if (environment != null) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            if (createdByUserId != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), createdByUserId));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
