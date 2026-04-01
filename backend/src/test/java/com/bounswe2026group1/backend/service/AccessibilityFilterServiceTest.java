package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteStepAccessibility;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessibilityFilterServiceTest {

    private final AccessibilityFilterService service = new AccessibilityFilterService();

    @Test
    void visuallyImpairedGetsHigherScoreWhenTactileAndSoundHintsPresent() {
        RegisteredUser u = new RegisteredUser();
        u.setName("t");
        u.setEmail("t@t.com");
        u.setPassword("password12");
        u.setRole("USER");
        Report construction = new Report(u, new Location(41.0, 29.0), "site", Tag.CONSTRUCTION);

        List<RouteStepAccessibility> steps = List.of(
                RouteStepAccessibility.builder()
                        .instruction("tactile paving and traffic light")
                        .maneuverType("turn")
                        .matchedOsmTagHints(List.of("tactile_paving", "traffic_signals:sound", "crossing"))
                        .build());

        int general = service.calculateAccessibilityScore(steps, false, List.of(construction));
        int vi = service.calculateAccessibilityScore(steps, true, List.of(construction));

        assertTrue(vi > general, "VI mode should score higher when tactile/sound hints are present");
    }
}
