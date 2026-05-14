package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LeaderboardVisibilityRequest;
import com.bounswe2026group1.backend.dto.VoiceCommandsRequest;
import com.bounswe2026group1.backend.dto.PointEventResponse;
import com.bounswe2026group1.backend.dto.UpdateProfileRequest;
import com.bounswe2026group1.backend.dto.UserProfileDTO;
import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.PointReason;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import com.bounswe2026group1.backend.service.S3MediaService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RegisteredUserController.class)
class RegisteredUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RegisteredUserService registeredUserService;
    @MockitoBean private S3MediaService s3MediaService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    private static final String OWNER_EMAIL = "owner@test.com";
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;

    private UserProfileDTO ownerProfile;

    @BeforeEach
    void setUp() {
        ownerProfile = UserProfileDTO.builder()
                .id(OWNER_ID)
                .name("Owner")
                .email(OWNER_EMAIL)
                .bio("hello")
                .role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder()
                        .reportsSubmitted(2).routesPlanned(1).build())
                .build();
    }

    // ───── GET /me ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("GET /me returns 200 with profile when authenticated")
    void me_authenticated_returns200() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);

        mockMvc.perform(get("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OWNER_ID))
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.contributionStats.reportsSubmitted").value(2))
                .andExpect(jsonPath("$.contributionStats.routesPlanned").value(1));
    }

    @Test
    @DisplayName("GET /me returns 401 when no auth")
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isUnauthorized());
    }

    // ───── DELETE /me (self-delete, 1.1.1.2.12) ──────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /me returns 204 and delegates to service")
    void deleteSelf_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());

        verify(registeredUserService).deleteSelf(OWNER_EMAIL);
    }

    @Test
    @DisplayName("DELETE /me returns 401 when no auth")
    void deleteSelf_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isUnauthorized());

        verify(registeredUserService, never()).deleteSelf(any());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /me returns 409 when caller is the last admin")
    void deleteSelf_lastAdmin_returns409() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Cannot delete the last admin"))
                .when(registeredUserService).deleteSelf(OWNER_EMAIL);

        mockMvc.perform(delete("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isConflict());
    }

    // ───── GET /{id} ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("GET /{id} returns 200")
    void getById_returns200() throws Exception {
        when(registeredUserService.getProfileById(OWNER_ID)).thenReturn(ownerProfile);

        mockMvc.perform(get("/api/users/{id}", OWNER_ID).header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OWNER_ID));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("GET /{id} returns 404 when user missing")
    void getById_returns404() throws Exception {
        when(registeredUserService.getProfileById(99L))
                .thenThrow(new NoSuchElementException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/{id}", 99L).header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── PUT /{id}/profile ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("PUT /{id}/profile by owner returns 200")
    void updateProfile_owner_returns200() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        UpdateProfileRequest req = new UpdateProfileRequest(null, "new bio");
        UserProfileDTO updated = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).bio("new bio").role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .build();
        when(registeredUserService.updateProfile(eq(OWNER_ID), any(UpdateProfileRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/{id}/profile", OWNER_ID)
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("new bio"));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("PUT /{id}/profile by non-owner returns 403")
    void updateProfile_nonOwner_returns403() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        UpdateProfileRequest req = new UpdateProfileRequest(null, "new bio");

        mockMvc.perform(put("/api/users/{id}/profile", OTHER_ID)
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(registeredUserService, never()).updateProfile(any(), any());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("PUT /{id}/profile rejects bio over 500 chars with 400")
    void updateProfile_invalidBio_returns400() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        UpdateProfileRequest req = new UpdateProfileRequest(null, "x".repeat(501));

        mockMvc.perform(put("/api/users/{id}/profile", OWNER_ID)
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ───── POST /{id}/profile/avatar ─────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("POST /{id}/profile/avatar by owner returns 201 with avatarUrl")
    void uploadAvatar_owner_returns201() throws Exception {
        String avatarUrl = "https://bucket.s3.amazonaws.com/uuid_pic.jpg";
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(s3MediaService.uploadFile(any())).thenReturn(avatarUrl);
        UserProfileDTO updated = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).avatarUrl(avatarUrl).role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .build();
        when(registeredUserService.setAvatar(eq(OWNER_ID), eq(avatarUrl))).thenReturn(updated);

        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/{id}/profile/avatar", OWNER_ID).file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avatarUrl").value(avatarUrl));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("POST /{id}/profile/avatar by non-owner returns 403")
    void uploadAvatar_nonOwner_returns403() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);

        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/{id}/profile/avatar", OTHER_ID).file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isForbidden());

        verify(s3MediaService, never()).uploadFile(any());
        verify(registeredUserService, never()).setAvatar(any(), any());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("POST /{id}/profile/avatar with unsupported file type returns 400")
    void uploadAvatar_invalidType_returns400() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(s3MediaService.uploadFile(any())).thenThrow(new IllegalArgumentException("Invalid file type."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/{id}/profile/avatar", OWNER_ID).file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("POST /{id}/profile/avatar with S3 failure returns 500")
    void uploadAvatar_s3Failure_returns500() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(s3MediaService.uploadFile(any())).thenThrow(new RuntimeException("S3 down"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/{id}/profile/avatar", OWNER_ID).file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isInternalServerError());
    }

    // ───── DELETE /{id}/profile/avatar ───────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /{id}/profile/avatar by owner returns 204 and clears DB + S3")
    void deleteAvatar_owner_returns204() throws Exception {
        String existing = "https://bucket.s3.amazonaws.com/uuid_pic.jpg";
        UserProfileDTO withAvatar = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).avatarUrl(existing).role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .build();
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.getProfileById(OWNER_ID)).thenReturn(withAvatar);

        mockMvc.perform(delete("/api/users/{id}/profile/avatar", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());

        verify(registeredUserService).setAvatar(OWNER_ID, null);
        verify(s3MediaService).deleteFile(existing);
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /{id}/profile/avatar with no existing avatar still returns 204 (no S3 call)")
    void deleteAvatar_noExistingAvatar_skipsS3() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.getProfileById(OWNER_ID)).thenReturn(ownerProfile); // avatarUrl null

        mockMvc.perform(delete("/api/users/{id}/profile/avatar", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());

        verify(registeredUserService).setAvatar(OWNER_ID, null);
        verify(s3MediaService, never()).deleteFile(any());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /{id}/profile/avatar still returns 204 even when S3 delete fails")
    void deleteAvatar_s3Failure_stillReturns204() throws Exception {
        String existing = "https://bucket.s3.amazonaws.com/uuid_pic.jpg";
        UserProfileDTO withAvatar = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).avatarUrl(existing).role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .build();
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.getProfileById(OWNER_ID)).thenReturn(withAvatar);
        doThrow(new RuntimeException("S3 down")).when(s3MediaService).deleteFile(existing);

        mockMvc.perform(delete("/api/users/{id}/profile/avatar", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());

        // DB clear must have happened before S3 call
        verify(registeredUserService).setAvatar(OWNER_ID, null);
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    @DisplayName("DELETE /{id}/profile/avatar by non-owner returns 403")
    void deleteAvatar_nonOwner_returns403() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);

        mockMvc.perform(delete("/api/users/{id}/profile/avatar", OTHER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isForbidden());

        verify(registeredUserService, never()).setAvatar(any(), any());
        verify(s3MediaService, never()).deleteFile(any());
    }

    // ───── GET /api/users (list all) ────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getAll_returnsServiceList() throws Exception {
        when(registeredUserService.getAllProfiles()).thenReturn(List.of(ownerProfile));

        mockMvc.perform(get("/api/users").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(OWNER_ID));
    }

    // ───── GET /api/users/search ────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void search_returns200_andCapsPageSize() throws Exception {
        Page<UserProfileDTO> page = new PageImpl<>(List.of(ownerProfile));
        when(registeredUserService.searchUsers(eq("ali"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/search")
                        .param("q", "ali")
                        .param("page", "-3")    // clamped to 0
                        .param("size", "999")    // clamped to 50
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(OWNER_ID));

        verify(registeredUserService).searchUsers(eq("ali"),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 50));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void search_returns400WhenServiceRejectsQuery() throws Exception {
        when(registeredUserService.searchUsers(eq(""), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("query must not be blank"));

        mockMvc.perform(get("/api/users/search")
                        .param("q", "")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isBadRequest());
    }

    // POST /api/users intentionally skipped: this is an "internal" endpoint that takes a raw
    // JPA RegisteredUser entity. Jackson 3 cannot deserialize that shape cleanly (lazy relations,
    // many enums with non-null Hibernate defaults), and the endpoint comment recommends clients
    // use POST /auth/register instead. One line of coverage isn't worth fighting Jackson here.

    // ───── DELETE /api/users/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void delete_returns204WhenServiceDeletes() throws Exception {
        when(registeredUserService.delete(OWNER_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/users/{id}", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void delete_returns404WhenServiceReportsMissing() throws Exception {
        when(registeredUserService.delete(404L)).thenReturn(false);

        mockMvc.perform(delete("/api/users/{id}", 404L)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── GET /me 404 path ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void me_returns404WhenUserNoLongerExists() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL))
                .thenThrow(new NoSuchElementException("user gone"));

        mockMvc.perform(get("/api/users/me").header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── 404 paths for updateProfile / uploadAvatar / deleteAvatar ────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void updateProfile_returns404WhenServiceReportsMissing() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.updateProfile(eq(OWNER_ID), any(UpdateProfileRequest.class)))
                .thenThrow(new NoSuchElementException("gone"));

        mockMvc.perform(put("/api/users/{id}/profile", OWNER_ID)
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(null, "bio"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void uploadAvatar_returns404WhenServiceReportsMissing() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(s3MediaService.uploadFile(any())).thenReturn("https://bucket.s3.amazonaws.com/a.jpg");
        when(registeredUserService.setAvatar(eq(OWNER_ID), any()))
                .thenThrow(new NoSuchElementException("gone"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/{id}/profile/avatar", OWNER_ID).file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void deleteAvatar_returns404WhenServiceReportsMissing() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.getProfileById(OWNER_ID))
                .thenThrow(new NoSuchElementException("gone"));

        mockMvc.perform(delete("/api/users/{id}/profile/avatar", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── GET /{id}/badges ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getBadges_returns200() throws Exception {
        when(registeredUserService.getBadges(OWNER_ID))
                .thenReturn(List.of(Badge.TRUSTED_REPORTER, Badge.TOP_10));

        mockMvc.perform(get("/api/users/{id}/badges", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getBadges_returns404WhenUserMissing() throws Exception {
        when(registeredUserService.getBadges(404L))
                .thenThrow(new NoSuchElementException("no user"));

        mockMvc.perform(get("/api/users/{id}/badges", 404L)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── GET /{id}/points/history ─────────────────────────────────────────

    private PointEventResponse pointEvent(long id, int delta) {
        return new PointEventResponse(id, delta, PointReason.REPORT_SUBMIT, null,
                Instant.parse("2026-05-01T10:00:00Z"));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getPointsHistory_ownerReceivesLedger() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        Page<PointEventResponse> page = new PageImpl<>(List.of(pointEvent(1L, 10)));
        when(registeredUserService.listPointEvents(eq(OWNER_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/{id}/points/history", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].delta").value(10));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getPointsHistory_nonOwnerNonAdminReturns403() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);

        mockMvc.perform(get("/api/users/{id}/points/history", OTHER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isForbidden());

        verify(registeredUserService, never()).listPointEvents(any(), any());
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    void getPointsHistory_adminCanViewOthers() throws Exception {
        UserProfileDTO admin = UserProfileDTO.builder()
                .id(9L).name("Admin").email("admin@test.com").role("ADMIN")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .build();
        when(registeredUserService.getProfileByEmail("admin@test.com")).thenReturn(admin);
        Page<PointEventResponse> page = new PageImpl<>(List.of(pointEvent(7L, -5)));
        when(registeredUserService.listPointEvents(eq(OTHER_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/{id}/points/history", OTHER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].delta").value(-5));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void getPointsHistory_returns404WhenLedgerLookupMisses() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        when(registeredUserService.listPointEvents(eq(OWNER_ID), any(Pageable.class)))
                .thenThrow(new NoSuchElementException("user has no ledger"));

        mockMvc.perform(get("/api/users/{id}/points/history", OWNER_ID)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    // ───── PATCH /me/leaderboard-visibility ─────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void setLeaderboardVisibility_authenticated_returns200() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        UserProfileDTO updated = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .leaderboardHidden(true)
                .build();
        when(registeredUserService.setLeaderboardHidden(OWNER_ID, true)).thenReturn(updated);

        mockMvc.perform(patch("/api/users/me/leaderboard-visibility")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaderboardVisibilityRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboardHidden").value(true));
    }

    @Test
    void setLeaderboardVisibility_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me/leaderboard-visibility")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaderboardVisibilityRequest(false))))
                .andExpect(status().isUnauthorized());

        verify(registeredUserService, never()).setLeaderboardHidden(any(), anyBoolean());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void setLeaderboardVisibility_returns404WhenUserGone() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL))
                .thenThrow(new NoSuchElementException("user gone"));

        mockMvc.perform(patch("/api/users/me/leaderboard-visibility")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaderboardVisibilityRequest(true))))
                .andExpect(status().isNotFound());
    }

    // ───── PATCH /me/voice-commands (1.1.3.8) ─────────────────────────────────

    @Test
    @WithMockUser(username = OWNER_EMAIL)
    void setVoiceCommands_authenticated_returns200() throws Exception {
        when(registeredUserService.getProfileByEmail(OWNER_EMAIL)).thenReturn(ownerProfile);
        UserProfileDTO updated = UserProfileDTO.builder()
                .id(OWNER_ID).name("Owner").email(OWNER_EMAIL).role("USER")
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder().build())
                .voiceCommandsEnabled(true)
                .build();
        when(registeredUserService.setVoiceCommandsEnabled(OWNER_ID, true)).thenReturn(updated);

        mockMvc.perform(patch("/api/users/me/voice-commands")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoiceCommandsRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceCommandsEnabled").value(true));
    }

    @Test
    void setVoiceCommands_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me/voice-commands")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoiceCommandsRequest(false))))
                .andExpect(status().isUnauthorized());

        verify(registeredUserService, never()).setVoiceCommandsEnabled(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
