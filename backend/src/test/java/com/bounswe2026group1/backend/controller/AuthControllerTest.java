package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import com.bounswe2026group1.backend.util.JwtUtil; // May be needed if SecurityConfig loads JwtAuthFilter
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Runs the test comprehensively by covering Security filters and other systems
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc // Bootstraps the entire MockMvc infrastructure and Security
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisteredUserService registeredUserService;

    // JwtUtil and Filter must be mocked in the test context because the Filter is injected when Security Configuration starts.
    // If your project has JwtAuthFilter and JwtUtil, we include it here because the Spring Context will look for it.
    @MockBean
    private JwtUtil jwtUtil;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setName("Test");
        validRegisterRequest.setEmail("test@test.com");
        validRegisterRequest.setPassword("StrongP@ss1");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test@test.com");
        validLoginRequest.setPassword("StrongP@ss1");
    }

    // --- REGISTER TESTS ---

    @Test
    @WithMockUser // Opens a mocked virtual user context to bypass CSRF or Security
    void register_Returns201_OnSuccess() throws Exception {
        RegisterResponse expectedResponse = new RegisterResponse(1L, "Test", "test@test.com", "USER");
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/register")
                        .with(csrf()) // If csrf is still not fully bypassed, we add a security mock
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @WithMockUser
    void register_Returns400_WhenValidationFails() throws Exception {
        // Name missing, Email in invalid format
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setName(""); // @NotBlank will fail
        invalidRequest.setEmail("not-an-email"); // @Email will fail
        invalidRequest.setPassword("StrongP@ss1");

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest()); // We guarantee that 400 will be returned
    }

    @Test
    @WithMockUser
    void register_Returns409_OnDuplicateEmail() throws Exception {
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Email is already in use."));

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Email is already in use."));
    }

    @Test
    @WithMockUser
    void register_Returns400_OnWeakPasswordExceptions() throws Exception {
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Password must be at least 8 characters long"));

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest()) // Should throw 400 Bad Request
                .andExpect(content().string("Password must be at least 8 characters long"));
    }

    // --- LOGIN TESTS ---

    @Test
    @WithMockUser
    void login_Returns200_OnSuccess() throws Exception {
        LoginResponse expectedResponse = new LoginResponse("mocked.jwt.token");
        Mockito.when(registeredUserService.loginUser(any(LoginRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"));
    }

    @Test
    @WithMockUser
    void login_Returns400_WhenValidationFails() throws Exception {
        // Validation Test for Login (empty email and pass)
        LoginRequest invalidLogin = new LoginRequest();
        invalidLogin.setEmail("");
        invalidLogin.setPassword("");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void login_Returns401_OnBadCredentials() throws Exception {
        Mockito.when(registeredUserService.loginUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid email or password"));
    }
}
