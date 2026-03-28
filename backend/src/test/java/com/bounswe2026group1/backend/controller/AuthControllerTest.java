package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import com.bounswe2026group1.backend.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisteredUserService registeredUserService;

    // JwtUtil must be mocked because JwtAuthFilter (a @Component filter) depends on it
    // and is loaded when the Security configuration starts.
    @MockitoBean
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
    void register_Returns201_OnSuccess() throws Exception {
        RegisterResponse expectedResponse = new RegisterResponse(1L, "Test", "test@test.com", "USER");
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    void register_Returns400_WhenValidationFails() throws Exception {
        // Name missing, Email in invalid format
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setName(""); // @NotBlank will fail
        invalidRequest.setEmail("not-an-email"); // @Email will fail
        invalidRequest.setPassword("StrongP@ss1");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_Returns409_OnDuplicateEmail() throws Exception {
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Email is already in use."));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Email is already in use."));
    }

    @Test
    void register_Returns400_OnWeakPasswordExceptions() throws Exception {
        Mockito.when(registeredUserService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Password must be at least 8 characters long"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Password must be at least 8 characters long"));
    }

    // --- LOGIN TESTS ---

    @Test
    void login_Returns200_OnSuccess() throws Exception {
        LoginResponse expectedResponse = new LoginResponse("mocked.jwt.token");
        Mockito.when(registeredUserService.loginUser(any(LoginRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"));
    }

    @Test
    void login_Returns400_WhenValidationFails() throws Exception {
        LoginRequest invalidLogin = new LoginRequest();
        invalidLogin.setEmail("");
        invalidLogin.setPassword("");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_Returns401_OnBadCredentials() throws Exception {
        Mockito.when(registeredUserService.loginUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid email or password"));
    }
}