package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * POST body for {@code /api/users/me/routing-profiles}. {@code name} is
 * required; {@code constraints} may be empty (an empty bundle is still a
 * valid choice).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomRoutingProfileRequest {

    @NotBlank
    @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
    private String name;

    private Set<RoutingConstraint> constraints;

    private TravelMode preferredTravelMode;
}
