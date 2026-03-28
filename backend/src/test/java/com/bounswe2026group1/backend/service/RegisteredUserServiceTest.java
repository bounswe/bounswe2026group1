package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisteredUserServiceTest {

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private RegisteredUserService registeredUserService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private RegisteredUser mockUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setName("Test User");
        validRegisterRequest.setEmail("test@test.com");
        validRegisterRequest.setPassword("StrongP@ss1");

        mockUser = new RegisteredUser(1L, "Test User", "test@test.com", "hashedPassword", "USER");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test@test.com");
        validLoginRequest.setPassword("StrongP@ss1");
    }

    // --- REGISTER TESTS ---

    @Test
    void registerUser_Success() {
        when(registeredUserRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(validRegisterRequest.getPassword())).thenReturn("hashedPassword");
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenReturn(mockUser);

        RegisterResponse response = registeredUserService.registerUser(validRegisterRequest);

        assertNotNull(response);
        assertEquals("test@test.com", response.getEmail());
        assertEquals(1L, response.getId());
        verify(registeredUserRepository, times(1)).save(any(RegisteredUser.class));
    }

    @Test
    void registerUser_Fail_DuplicateEmail() {
        when(registeredUserRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registeredUserService.registerUser(validRegisterRequest);
        });

        assertTrue(exception.getMessage().contains("already in use"));
        verify(registeredUserRepository, never()).save(any(RegisteredUser.class));
    }

    @Test
    void registerUser_Fail_WeakPassword() {
        validRegisterRequest.setPassword("weak");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registeredUserService.registerUser(validRegisterRequest);
        });

        assertTrue(exception.getMessage().contains("Password must be at least 8 characters long"));
        verify(registeredUserRepository, never()).existsByEmail(any());
        verify(registeredUserRepository, never()).save(any(RegisteredUser.class));
    }

    // --- LOGIN TESTS ---

    @Test
    void loginUser_Success() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(mockUser.getId(), mockUser.getEmail(), mockUser.getRole())).thenReturn("mockJwtToken");

        LoginResponse response = registeredUserService.loginUser(validLoginRequest);

        assertNotNull(response);
        assertEquals("mockJwtToken", response.getToken());
    }

    @Test
    void loginUser_Fail_UserNotFound() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> {
            registeredUserService.loginUser(validLoginRequest);
        });
    }

    @Test
    void loginUser_Fail_WrongPassword() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> {
            registeredUserService.loginUser(validLoginRequest);
        });
    }
}
