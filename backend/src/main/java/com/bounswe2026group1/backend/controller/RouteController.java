package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.RouteSummaryDTO;
import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.RouteRepository;
import com.bounswe2026group1.backend.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Accessible route planning that detours around verified obstacles.")
public class RouteController {

    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final RegisteredUserRepository registeredUserRepository;

    @PostMapping
    @Operation(
            summary = "Plan accessible routes between two points",
            description = "Returns one or more route options from `(startLat, startLon)` to " +
                    "`(endLat, endLon)` for the requested travel mode, detouring around verified " +
                    "obstacle reports. Anonymous; if a Bearer token is supplied, the planned route " +
                    "is also recorded against the caller's contribution stats."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One or more route options."),
            @ApiResponse(responseCode = "400", description = "Invalid coordinates or travel mode."),
            @ApiResponse(responseCode = "502", description = "Upstream routing provider failure.")
    })
    public ResponseEntity<List<RouteResponse>> getRouteOptions(@RequestBody RouteRequest request) {
        // Look up the caller once: we need their constraints + preferred mode for routing,
        // and the user object again for recording the planned route. Anonymous → null.
        RegisteredUser caller = currentUserOrNull();

        // Three-state contract for the constraints argument (issue #544):
        //   • null     — signed-in user with NONE preset; skip avoidance entirely.
        //   • Set.of() — anonymous baseline; avoid every verified obstacle.
        //   • non-empty — filter avoid set to hazards the user actually cares about.
        Set<RoutingConstraint> constraints;
        if (caller == null) {
            constraints = Set.of();
        } else if (caller.getPreferredPreset() == RoutingPreset.NONE) {
            constraints = null;
        } else {
            constraints = caller.getRoutingConstraints() != null
                    ? caller.getRoutingConstraints()
                    : Set.of();
        }
        TravelMode preferredMode = caller != null ? caller.getPreferredTravelMode() : null;

        List<RouteResponse> options = routeService.getRouteOptions(request, constraints, preferredMode);
        if (caller != null) {
            recordPlannedRouteForUser(caller, request, options);
        }
        return ResponseEntity.ok(options);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List routes a given user has planned")
    public List<RouteSummaryDTO> getByUserId(@PathVariable Long userId) {
        return routeRepository.findByCreatedByIdOrderByIdDesc(userId).stream()
                .map(RouteSummaryDTO::from)
                .toList();
    }

    /**
     * Resolve the authenticated caller's full {@link RegisteredUser} record so its
     * routing-preference fields are available alongside the user's id. Returns null
     * for anonymous callers and for tokens whose subject isn't in the DB.
     */
    private RegisteredUser currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated()
                || auth.getName() == null) {
            return null;
        }
        return registeredUserRepository.findByEmail(auth.getName()).orElse(null);
    }

    /**
     * Persist a single Route record so authenticated users get credited with a "route planned"
     * contribution stat (issue #302). Failures are logged, not propagated, so a flaky
     * persistence layer can never fail a successful routing response.
     */
    private void recordPlannedRouteForUser(RegisteredUser user, RouteRequest request, List<RouteResponse> options) {
        if (options == null || options.isEmpty()) return;

        try {
            RouteResponse first = options.get(0);
            Route record = Route.builder()
                    .startLocation(new Location(request.getStartLat(), request.getStartLon()))
                    .endLocation(new Location(request.getEndLat(), request.getEndLon()))
                    .distance((int) Math.round(first.getDistanceMeters()))
                    .duration((int) Math.round(first.getDurationSeconds()))
                    .travelMode(first.getMode())
                    .createdBy(user)
                    .build();
            routeRepository.save(record);
        } catch (Exception e) {
            log.warn("Failed to persist planned route for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
