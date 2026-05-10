package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * PUT body for {@code /api/users/me/routing-profiles/{id}}. All fields
 * optional — null fields are ignored, letting callers patch one attribute at
 * a time. Sending a non-null {@code constraints} replaces the stored set
 * (including with an empty set).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomRoutingProfileRequest {

    @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
    private String name;

    private Set<RoutingConstraint> constraints;

    private TravelMode preferredTravelMode;
}
