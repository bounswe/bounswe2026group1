package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in the {@code availableConstraints} catalog returned by
 * {@code GET /api/users/me/routing-preferences}. Lets the frontend render
 * a self-describing settings page without hard-coding labels.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingConstraintInfo {
    private String name;
    private String label;
    private String description;

    public static RoutingConstraintInfo fromEnum(RoutingConstraint constraint) {
        return new RoutingConstraintInfo(constraint.name(), constraint.getLabel(), constraint.getDescription());
    }
}
