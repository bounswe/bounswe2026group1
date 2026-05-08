package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * PUT body for {@code /api/users/me/routing-preferences}. All fields optional;
 * the service interprets which combination the caller sent:
 *
 * <ul>
 *   <li>Only {@code preferredPreset} → constraints + travelMode are filled
 *       from the preset definition.</li>
 *   <li>Only {@code constraints} → preset is auto-detected via
 *       {@link RoutingPreset#detectFromConstraints(Set)}.</li>
 *   <li>Both → constraints win; preset becomes whatever
 *       {@code detectFromConstraints} returns.</li>
 *   <li>Neither → no-op (returns current state).</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoutingPreferencesRequest {
    private RoutingPreset preferredPreset;
    private Set<RoutingConstraint> constraints;
    private TravelMode preferredTravelMode;
}
