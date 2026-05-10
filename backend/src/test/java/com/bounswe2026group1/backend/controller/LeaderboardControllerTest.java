package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.LeaderboardEntry;
import com.bounswe2026group1.backend.service.LeaderboardService;
import com.bounswe2026group1.backend.service.RankInfo;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaderboardController.class)
class LeaderboardControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LeaderboardService leaderboardService;
    @MockitoBean private RegisteredUserRepository userRepository;
    @MockitoBean private JwtUtil jwtUtil;

    @Value("${app.api.key}")
    private String validApiKey;

    private static final String CALLER_EMAIL = "caller@test.com";

    @Test
    @DisplayName("GET /api/leaderboard returns top-N anonymously, no caller rank")
    void getLeaderboard_anonymous_returnsEntriesAndNullRank() throws Exception {
        when(leaderboardService.getTopN()).thenReturn(List.of(
                new LeaderboardEntry(1, 10L, "Alice", null, 200),
                new LeaderboardEntry(2, 11L, "Bob", null, 150)
        ));

        mockMvc.perform(get("/api/leaderboard").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].userId").value(10))
                .andExpect(jsonPath("$.entries[0].points").value(200))
                .andExpect(jsonPath("$.callerRank").doesNotExist());

        // Anonymous caller — no user lookup, no rank fetch.
        verify(userRepository, never()).findByEmail(any());
        verify(leaderboardService, never()).getRankFor(any());
    }

    @Test
    @WithMockUser(username = CALLER_EMAIL)
    @DisplayName("GET /api/leaderboard includes callerRank when authenticated")
    void getLeaderboard_authenticated_includesCallerRank() throws Exception {
        RegisteredUser caller = new RegisteredUser();
        caller.setId(42L);
        caller.setEmail(CALLER_EMAIL);

        when(leaderboardService.getTopN()).thenReturn(List.of(
                new LeaderboardEntry(1, 10L, "Alice", null, 200)
        ));
        when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(caller));
        when(leaderboardService.getRankFor(42L)).thenReturn(Optional.of(new RankInfo(7, 95)));

        mockMvc.perform(get("/api/leaderboard").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.callerRank.rank").value(7))
                .andExpect(jsonPath("$.callerRank.points").value(95));
    }

    @Test
    @WithMockUser(username = CALLER_EMAIL)
    @DisplayName("GET /api/leaderboard yields null callerRank when caller has opted out")
    void getLeaderboard_authenticatedButOptedOut_callerRankNull() throws Exception {
        RegisteredUser caller = new RegisteredUser();
        caller.setId(42L);
        caller.setEmail(CALLER_EMAIL);
        caller.setLeaderboardHidden(true);

        when(leaderboardService.getTopN()).thenReturn(List.of());
        when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(caller));
        when(leaderboardService.getRankFor(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/leaderboard").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callerRank").doesNotExist());
    }
}
