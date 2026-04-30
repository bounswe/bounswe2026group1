package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.RouteRepository;
import com.bounswe2026group1.backend.service.RouteService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RouteController.class)
class RouteControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RouteService routeService;
    @MockitoBean private RouteRepository routeRepository;
    @MockitoBean private RegisteredUserRepository registeredUserRepository;
    @MockitoBean private JwtUtil jwtUtil;

    @Value("${app.api.key}")
    private String validApiKey;

    @Test
    @DisplayName("GET /api/routes/user/{userId}: returns RouteSummaryDTOs for that user")
    void getByUserId_returnsRoutes() throws Exception {
        RegisteredUser ada = new RegisteredUser();
        ada.setId(7L);
        ada.setEmail("ada@test.com");

        Route r1 = Route.builder()
                .id(101L)
                .startLocation(new Location(41.0, 28.9))
                .endLocation(new Location(41.01, 28.95))
                .distance(500)
                .duration(600)
                .travelMode(TravelMode.WALKING)
                .createdBy(ada)
                .build();
        Route r2 = Route.builder()
                .id(100L)
                .startLocation(new Location(41.05, 29.00))
                .endLocation(new Location(41.06, 29.01))
                .distance(200)
                .duration(240)
                .travelMode(TravelMode.WHEELCHAIR)
                .createdBy(ada)
                .build();

        when(routeRepository.findByCreatedByIdOrderByIdDesc(7L)).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/routes/user/{userId}", 7L)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].distance").value(500))
                .andExpect(jsonPath("$[0].travelMode").value("WALKING"))
                .andExpect(jsonPath("$[0].createdById").value(7))
                .andExpect(jsonPath("$[1].id").value(100))
                .andExpect(jsonPath("$[1].travelMode").value("WHEELCHAIR"));
    }

    @Test
    @DisplayName("GET /api/routes/user/{userId}: empty list for user with no routes")
    void getByUserId_empty() throws Exception {
        when(routeRepository.findByCreatedByIdOrderByIdDesc(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/routes/user/{userId}", 99L)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
