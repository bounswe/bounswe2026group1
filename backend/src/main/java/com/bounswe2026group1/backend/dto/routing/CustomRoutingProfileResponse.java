package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserCustomRoutingProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Public view of a {@link UserCustomRoutingProfile}. Constraints are returned
 * as sorted enum names (matching the wire shape used by
 * {@link RoutingPreferencesResponse#getConstraints()}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRoutingProfileResponse {

    private Long id;
    private String name;
    private List<String> constraints;
    private TravelMode preferredTravelMode;
    private Instant createdAt;
    private Instant updatedAt;

    public static CustomRoutingProfileResponse fromEntity(UserCustomRoutingProfile profile) {
        Set<RoutingConstraint> stored = profile.getConstraints() == null
                ? Set.of()
                : profile.getConstraints();
        List<String> constraintNames = stored.stream()
                .map(RoutingConstraint::name)
                .sorted()
                .toList();
        return CustomRoutingProfileResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .constraints(constraintNames)
                .preferredTravelMode(profile.getPreferredTravelMode())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
