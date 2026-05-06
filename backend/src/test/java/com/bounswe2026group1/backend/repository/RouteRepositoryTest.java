package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RouteRepositoryTest extends AbstractPostgisIntegrationTest {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

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

    @Test
    void countByCreatedByIdIn_groupsCountsPerUser_omittingUsersWithNoRoutes() {
        RegisteredUser ada = persistUser("ada-batch@test.com");
        RegisteredUser bob = persistUser("bob-batch@test.com");
        RegisteredUser cleo = persistUser("cleo-batch@test.com"); // no routes
        persistRoute(ada, 100);
        persistRoute(ada, 200);
        persistRoute(bob, 300);

        List<Object[]> rows = routeRepository.countByCreatedByIdIn(
                List.of(ada.getId(), bob.getId(), cleo.getId()));

        assertEquals(2, rows.size(), "users with zero routes should not appear in the result");
        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            Long count = (Long) row[1];
            if (userId.equals(ada.getId())) assertEquals(2L, count);
            else if (userId.equals(bob.getId())) assertEquals(1L, count);
            else throw new AssertionError("unexpected userId in result: " + userId);
        }
    }

    @Test
    void findByCreatedByIdOrderByIdDesc_returnsOnlyUsersRoutes_newestFirst() {
        RegisteredUser ada = persistUser("ada@test.com");
        RegisteredUser bob = persistUser("bob@test.com");

        Route adaFirst  = persistRoute(ada, 100);
        Route adaSecond = persistRoute(ada, 200);
        Route bobOnly   = persistRoute(bob, 300);

        List<Route> adaRoutes = routeRepository.findByCreatedByIdOrderByIdDesc(ada.getId());
        assertEquals(2, adaRoutes.size());
        assertTrue(adaRoutes.get(0).getId() > adaRoutes.get(1).getId(), "results must be newest-first");
        assertEquals(adaSecond.getId(), adaRoutes.get(0).getId());
        assertEquals(adaFirst.getId(), adaRoutes.get(1).getId());

        List<Route> bobRoutes = routeRepository.findByCreatedByIdOrderByIdDesc(bob.getId());
        assertEquals(1, bobRoutes.size());
        assertEquals(bobOnly.getId(), bobRoutes.get(0).getId());

        // unrelated user gets an empty list, not the whole table
        List<Route> ghost = routeRepository.findByCreatedByIdOrderByIdDesc(9_999L);
        assertTrue(ghost.isEmpty());
    }

    private RegisteredUser persistUser(String email) {
        RegisteredUser u = new RegisteredUser();
        u.setName("Test");
        u.setEmail(email);
        u.setPassword("hashedPassword");
        u.setRole(UserRole.USER);
        return registeredUserRepository.save(u);
    }

    private Route persistRoute(RegisteredUser user, int distance) {
        Route r = Route.builder()
                .startLocation(new Location(41.0, 28.9))
                .endLocation(new Location(41.01, 28.95))
                .distance(distance)
                .duration(distance * 2)
                .travelMode(TravelMode.WALKING)
                .createdBy(user)
                .build();
        return routeRepository.save(r);
    }
}
