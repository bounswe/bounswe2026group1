package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private static final double REPORT_SCAN_PADDING_DEGREES = 0.002;
    private static final double MAX_STOP_DISTANCE_TO_LINE_DEGREES = 0.001;
    private static final int MAX_POSITIVE_STOPS = 3;

    private final ExternalRoutingService externalRoutingService;
    private final ReportRepository reportRepository;

    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());
        BBox requestBbox = computeStartEndBbox(start, end, REPORT_SCAN_PADDING_DEGREES);
        // Excludes only REJECTED; PENDING and VERIFIED reports are included (verified still weighted higher in penalties).
        List<Report> nearbyReports = reportRepository.findActiveReportsInBoundingBox(
                requestBbox.minLat, requestBbox.maxLat, requestBbox.minLon, requestBbox.maxLon, ReportStatus.REJECTED);
        log.info("Loaded {} reports in request bbox (status != REJECTED; param excludes REJECTED)", nearbyReports.size());

        // 1) Normal route for all user types: plain walking call.
        RoutingDirectionsResult walkingRoute = externalRoutingService.fetchDirections(start, end, TravelMode.WALKING);

        // 2) Wheelchair route + negative report check in route corridor.
        RoutingDirectionsResult wheelchairCheckedRoute =
                externalRoutingService.fetchDirections(start, end, TravelMode.WHEELCHAIR);

        // 3) Wheelchair route with positive crowdsourced stops (waypoints).
        List<Location> positiveStops = selectPositiveStops(start, end, nearbyReports);
        RoutingDirectionsResult wheelchairWithStops =
                externalRoutingService.fetchDirections(start, end, TravelMode.WHEELCHAIR, positiveStops);

        return List.of(
                toRouteResponse(walkingRoute, TravelMode.WALKING, "Normal Walking Route"),
                toRouteResponse(wheelchairCheckedRoute, TravelMode.WHEELCHAIR, "Wheelchair Route (Crowd Checked)"),
                toRouteResponse(wheelchairWithStops, TravelMode.WHEELCHAIR, "Wheelchair Route (Positive Stops)"));
    }

    private static RouteResponse toRouteResponse(
            RoutingDirectionsResult result,
            TravelMode mode,
            String routeLabel) {
        return RouteResponse.builder()
                .routeLabel(routeLabel)
                .distanceMeters(result.getDistanceMeters())
                .durationSeconds(result.getDurationSeconds())
                .mode(mode)
                .geometry(result.getGeometry())
                .steps(result.getSteps())
                .nodeCoordinates(result.getNodeCoordinates())
                .build();
    }

    private List<Location> selectPositiveStops(Location start, Location end, List<Report> nearbyReports) {
        // Positive stops: OTHER tag, not rejected, strictly more agrees than disagrees (PENDING or VERIFIED).
        return nearbyReports.stream()
                .filter(report -> report != null
                        && report.getLocation() != null
                        && report.getTag() == Tag.OTHER
                        && report.getStatus() != ReportStatus.REJECTED
                        && report.getAgrees() > report.getDisagrees())
                .filter(report -> isCloseToLine(start, end, report.getLocation()))
                .sorted(Comparator.comparingInt((Report r) -> r.getAgrees() - r.getDisagrees()).reversed())
                .limit(MAX_POSITIVE_STOPS)
                .map(Report::getLocation)
                .toList();
    }

    private boolean isCloseToLine(Location start, Location end, Location point) {
        double x0 = point.getLongitude();
        double y0 = point.getLatitude();
        double x1 = start.getLongitude();
        double y1 = start.getLatitude();
        double x2 = end.getLongitude();
        double y2 = end.getLatitude();

        double denominator = Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));
        if (denominator == 0.0) {
            return true;
        }
        double distance = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1) / denominator;
        log.debug("Positive stop distance-to-line={} degrees for point lat={}, lon={}", distance, y0, x0);
        return distance < MAX_STOP_DISTANCE_TO_LINE_DEGREES;
    }

    private static BBox computeStartEndBbox(Location start, Location end, double pad) {
        double minLat = Math.min(start.getLatitude(), end.getLatitude()) - pad;
        double maxLat = Math.max(start.getLatitude(), end.getLatitude()) + pad;
        double minLon = Math.min(start.getLongitude(), end.getLongitude()) - pad;
        double maxLon = Math.max(start.getLongitude(), end.getLongitude()) + pad;
        return new BBox(minLat, maxLat, minLon, maxLon);
    }

    private record BBox(double minLat, double maxLat, double minLon, double maxLon) { }
}
