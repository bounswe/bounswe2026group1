package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteStepAccessibility;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes a 0–100 accessibility score for walking routes using nearby {@link Report} data
 * and OSM-aligned hints on {@link RouteStepAccessibility} (e.g. tactile paving, crossings).
 */
@Service
@Slf4j
public class AccessibilityFilterService {

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 100;

    /**
     * @param steps               Parsed ORS steps with {@link RouteStepAccessibility#getMatchedOsmTagHints()}
     * @param isVisuallyImpaired  When true, applies stronger bonuses for tactile and audible signal hints.
     * @param nearbyReports       Reports considered relevant to the route corridor (e.g. bounding-box query).
     * @return Integer score in [0, 100]
     */
    public int calculateAccessibilityScore(
            List<RouteStepAccessibility> steps,
            boolean isVisuallyImpaired,
            List<Report> nearbyReports) {

        List<RouteStepAccessibility> safeSteps = steps == null ? List.of() : steps;
        List<Report> safeReports = nearbyReports == null ? List.of() : nearbyReports;

        int score = SCORE_MAX;

        for (Report report : safeReports) {
            if (report == null || report.getTag() == null || report.getStatus() == ReportStatus.REJECTED) {
                continue;
            }
            int penalty = penaltyForReport(report);
            score += penalty;
        }

        for (RouteStepAccessibility step : safeSteps) {
            if (step == null || step.getMatchedOsmTagHints() == null) {
                continue;
            }
            for (String hint : step.getMatchedOsmTagHints()) {
                if (hint == null) {
                    continue;
                }
                score += bonusForOsmHint(hint, isVisuallyImpaired);
            }
        }

        int clamped = clamp(score);
        log.debug(
                "Accessibility score: raw after clamp={}, visuallyImpaired={}, steps={}, reports={}",
                clamped,
                isVisuallyImpaired,
                safeSteps.size(),
                safeReports.size());
        return clamped;
    }

    /**
     * Negative impact from user reports (construction, broken infrastructure, etc.).
     * Verified reports count more than pending ones.
     */
    private int penaltyForReport(Report report) {
        Tag tag = report.getTag();
        int base = switch (tag) {
            case CONSTRUCTION -> 22;
            case BROKEN_ELEVATOR -> 18;
            case MISSING_RAMP -> 16;
            case NARROW_PASSAGE -> 14;
            case WET_FLOOR -> 10;
            case OTHER -> 6;
        };
        double factor = report.getStatus() == ReportStatus.VERIFIED ? 1.5 : 1.0;
        return -Math.toIntExact(Math.round(base * factor));
    }

    /**
     * Positive adjustments when step hints align with OSM-style accessibility features.
     * Visually impaired users get larger bonuses for tactile paving and audible traffic signals.
     */
    private int bonusForOsmHint(String hint, boolean isVisuallyImpaired) {
        return switch (hint) {
            case "tactile_paving" -> isVisuallyImpaired ? 25 : 8;
            case "traffic_signals:sound" -> isVisuallyImpaired ? 25 : 8;
            case "crossing" -> isVisuallyImpaired ? 10 : 4;
            default -> 0;
        };
    }

    private static int clamp(int value) {
        return Math.max(SCORE_MIN, Math.min(SCORE_MAX, value));
    }
}
