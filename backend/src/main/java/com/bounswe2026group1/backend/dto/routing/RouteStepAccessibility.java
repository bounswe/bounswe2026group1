package com.bounswe2026group1.backend.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStepAccessibility {

    private String instruction;
    private String maneuverType;
    private List<String> matchedOsmTagHints;
}
