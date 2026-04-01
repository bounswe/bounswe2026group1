package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.TravelMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class RouteRepositoryTest {

    @Autowired
    private RouteRepository routeRepository;

    @Test
    void saveAndFindById_persistsEmbeddedLocationsAndEnums() {
        Location start = new Location(41.015137, 28.979530);
        Location end = new Location(41.008583, 28.980175);

        Route route = Route.builder()
                .startLocation(start)
                .endLocation(end)
                .distance(850)
                .duration(620)
                .travelMode(TravelMode.WHEELCHAIR)
                .build();

        Route saved = routeRepository.save(route);
        assertTrue(saved.getId() != null && saved.getId() > 0);

        Optional<Route> loaded = routeRepository.findById(saved.getId());
        assertTrue(loaded.isPresent());
        Route found = loaded.get();

        assertEquals(start.getLatitude(), found.getStartLocation().getLatitude());
        assertEquals(start.getLongitude(), found.getStartLocation().getLongitude());
        assertEquals(end.getLatitude(), found.getEndLocation().getLatitude());
        assertEquals(end.getLongitude(), found.getEndLocation().getLongitude());
        assertEquals(850, found.getDistance());
        assertEquals(620, found.getDuration());
        assertEquals(TravelMode.WHEELCHAIR, found.getTravelMode());
    }
}
