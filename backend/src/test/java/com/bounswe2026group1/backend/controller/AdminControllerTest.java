package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.admin.AdminChangeRoleRequest;
import com.bounswe2026group1.backend.dto.admin.AdminCreateUserRequest;
import com.bounswe2026group1.backend.dto.admin.AdminUserResponse;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.*;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Security infrastructure mocks
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private RegisteredUserRepository registeredUserRepository;

    // Admin service mocks
    @MockitoBean
    private AdminUserService adminUserService;
    @MockitoBean
    private AdminReportService adminReportService;
    @MockitoBean
    private AdminCommentService adminCommentService;
    @MockitoBean
    private AdminValidationService adminValidationService;

    // -------------------------------------------------------------------------
    // User Management
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void listUsers_Returns200_WithAdminRole() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void listUsers_Returns200_WithFilters() throws Exception {
        AdminUserResponse user = new AdminUserResponse();
        user.setId(2L);
        user.setEmail("user@test.com");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.BANNED);

        when(adminUserService.listUsers(eq(UserStatus.BANNED), eq(UserRole.USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/admin/users")
                        .param("status", "BANNED")
                        .param("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("user@test.com"))
                .andExpect(jsonPath("$.content[0].status").value("BANNED"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void createUser_Returns201_OnSuccess() throws Exception {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setEmail("new@test.com");
        req.setName("New User");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.USER);

        AdminUserResponse resp = new AdminUserResponse();
        resp.setId(5L);
        resp.setEmail("new@test.com");
        resp.setRole(UserRole.USER);
        resp.setStatus(UserStatus.ACTIVE);

        when(adminUserService.createUser(any())).thenReturn(resp);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@test.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void banUser_Returns200_OnSuccess() throws Exception {
        // @AuthenticationPrincipal resolves to null with @WithMockUser (UserDetails ≠ String), so use any()
        doNothing().when(adminUserService).ban(eq(2L), any());

        mockMvc.perform(patch("/api/admin/users/2/ban"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void banUser_Returns403_WhenSelfBan() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Admins cannot ban yourself"))
                .when(adminUserService).ban(eq(1L), any());

        mockMvc.perform(patch("/api/admin/users/1/ban"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void unbanUser_Returns200_OnSuccess() throws Exception {
        doNothing().when(adminUserService).unban(2L);

        mockMvc.perform(patch("/api/admin/users/2/unban"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteUser_Returns204_OnSuccess() throws Exception {
        doNothing().when(adminUserService).deleteUser(eq(2L), any());

        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteUser_Returns404_WhenNotFound() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "User not found with id: 99"))
                .when(adminUserService).deleteUser(eq(99L), any());

        mockMvc.perform(delete("/api/admin/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void changeRole_Returns200_OnSuccess() throws Exception {
        AdminChangeRoleRequest req = new AdminChangeRoleRequest();
        req.setRole(UserRole.ADMIN);

        AdminUserResponse resp = new AdminUserResponse();
        resp.setId(2L);
        resp.setRole(UserRole.ADMIN);

        when(adminUserService.changeRole(eq(2L), any(), any())).thenReturn(resp);

        mockMvc.perform(patch("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void changeRole_Returns409_WhenLastAdmin() throws Exception {
        AdminChangeRoleRequest req = new AdminChangeRoleRequest();
        req.setRole(UserRole.USER);

        doThrow(new ResponseStatusException(CONFLICT, "Cannot demote the last admin"))
                .when(adminUserService).changeRole(any(), any(), any());

        mockMvc.perform(patch("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // Report Management
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void listReports_Returns200() throws Exception {
        when(adminReportService.listReports(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteReport_Returns204_OnSuccess() throws Exception {
        doNothing().when(adminReportService).deleteReport(10L);

        mockMvc.perform(delete("/api/admin/reports/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteReport_Returns404_WhenNotFound() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Report not found with id: 99"))
                .when(adminReportService).deleteReport(99L);

        mockMvc.perform(delete("/api/admin/reports/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Comment Management
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteComment_Returns204_OnSuccess() throws Exception {
        doNothing().when(adminCommentService).deleteComment(5L);

        mockMvc.perform(delete("/api/admin/comments/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteComment_Returns404_WhenNotFound() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Comment not found with id: 99"))
                .when(adminCommentService).deleteComment(99L);

        mockMvc.perform(delete("/api/admin/comments/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Validation Management
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteValidation_Returns204_OnSuccess() throws Exception {
        doNothing().when(adminValidationService).deleteValidation(7L);

        mockMvc.perform(delete("/api/admin/validations/7"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void deleteValidation_Returns404_WhenNotFound() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Verification not found with id: 99"))
                .when(adminValidationService).deleteValidation(99L);

        mockMvc.perform(delete("/api/admin/validations/99"))
                .andExpect(status().isNotFound());
    }
}
