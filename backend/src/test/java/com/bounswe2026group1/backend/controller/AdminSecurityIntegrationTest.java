package com.bounswe2026group1.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // Unauthenticated → 401
    // -------------------------------------------------------------------------

    @Test
    void getUsers_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReports_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReport_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/admin/reports/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteComment_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/admin/comments/1"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Authenticated but USER role → 403
    // -------------------------------------------------------------------------

    @Test
    void getUsers_Returns403_WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReports_Returns403_WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReport_Returns403_WhenNotAdmin() throws Exception {
        mockMvc.perform(delete("/api/admin/reports/1")
                        .with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void banUser_Returns403_WhenNotAdmin() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/ban")
                        .with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // ADMIN role → 200 (empty page when DB is empty)
    // -------------------------------------------------------------------------

    @Test
    void getUsers_Returns200_WhenAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void getReports_Returns200_WhenAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
