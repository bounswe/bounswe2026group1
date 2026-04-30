package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.RouteRepository;
import com.bounswe2026group1.backend.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final RegisteredUserRepository registeredUserRepository;

    @PostMapping
    public ResponseEntity<List<RouteResponse>> getRouteOptions(@RequestBody RouteRequest request) {
        List<RouteResponse> options = routeService.getRouteOptions(request);
        recordPlannedRouteIfAuthenticated(request, options);
        return ResponseEntity.ok(options);
    }

    /**
     * Persist a single Route record so authenticated users can be credited with a "route planned"
     * contribution stat (issue #302). Anonymous calls are not recorded.
     */
    private void recordPlannedRouteIfAuthenticated(RouteRequest request, List<RouteResponse> options) {
        if (options == null || options.isEmpty()) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return;

        RegisteredUser user = registeredUserRepository.findByEmail(auth.getName()).orElse(null);
        if (user == null) return;

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
            // Don't fail the routing response if persistence fails — just log it.
            log.warn("Failed to persist planned route for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
