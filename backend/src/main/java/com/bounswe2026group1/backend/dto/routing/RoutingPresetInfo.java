package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One entry in the {@code availablePresets} catalog returned by
 * {@code GET /api/users/me/routing-preferences}. The {@code constraints} field
 * lets the frontend preview which constraints a preset will apply before the
 * user commits to it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingPresetInfo {
    private String name;
    private String label;
    private List<String> constraints;
    private TravelMode defaultTravelMode;

    public static RoutingPresetInfo fromEnum(RoutingPreset preset) {
        List<String> constraintNames = preset.getConstraints().stream()
                .map(RoutingConstraint::name)
                .sorted()
                .toList();
        return new RoutingPresetInfo(
                preset.name(),
                preset.getLabel(),
                constraintNames,
                preset.getDefaultTravelMode());
    }
}
