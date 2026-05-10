package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.LeaderboardResponse;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.LeaderboardService;
import com.bounswe2026group1.backend.service.RankInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard", description = "Public top-N ranking and caller's own rank.")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final RegisteredUserRepository userRepository;

    @GetMapping
    @Operation(
            summary = "Top-N leaderboard with caller's rank",
            description = "Public endpoint. Returns the top 100 users ranked by points. " +
                    "When the caller is authenticated and not opted out, their own rank is " +
                    "included alongside the entries."
    )
    @ApiResponse(responseCode = "200", description = "Leaderboard payload.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<LeaderboardResponse> getLeaderboard() {
        RankInfo callerRank = currentUserEmailOrNull()
                .flatMap(email -> userRepository.findByEmail(email))
                .map(RegisteredUser::getId)
                .flatMap(leaderboardService::getRankFor)
                .orElse(null);
        return ResponseEntity.ok(new LeaderboardResponse(
                leaderboardService.getTopN(),
                callerRank));
    }

    private static java.util.Optional<String> currentUserEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated()
                || auth.getName() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(auth.getName());
    }
}
