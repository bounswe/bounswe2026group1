package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.dto.UpdateProfileRequest;
import com.bounswe2026group1.backend.dto.UserProfileDTO;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.RouteRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisteredUserServiceTest {

    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

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

        mockUser = new RegisteredUser();
        mockUser.setId(1L);
        mockUser.setName("Test User");
        mockUser.setEmail("test@test.com");
        mockUser.setPassword("hashedPassword");
        mockUser.setRole("USER");
        mockUser.setBio("hello");
        mockUser.setAvatarUrl("https://cdn/old.jpg");

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

        // Mock existsByEmail to return false so that we can test the weak password scenario
        when(registeredUserRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registeredUserService.registerUser(validRegisterRequest);
        });

        // Search for a substring in the exception message to confirm it's about password strength
        assertTrue(exception.getMessage().contains("Password must be"));

        // Verify that existsByEmail was called once, but save was never called
        verify(registeredUserRepository, times(1)).existsByEmail(validRegisterRequest.getEmail());
        verify(registeredUserRepository, never()).save(any(RegisteredUser.class));
    }

    // --- LOGIN TESTS ---

    @Test
    void loginUser_Success() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(mockUser.getId(), mockUser.getEmail(), mockUser.getRole())).thenReturn("mockJwtToken");
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        LoginResponse response = registeredUserService.loginUser(validLoginRequest);

        assertNotNull(response);
        assertEquals("mockJwtToken", response.getToken());
        assertNotNull(response.getProfile());
        assertEquals(1L, response.getProfile().getId());
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

    // ───── PROFILE TESTS (issue #302) ────────────────────────────────────────

    @Test
    void getAllProfiles_usesBatchQueries_noPerUserCounts() {
        RegisteredUser other = new RegisteredUser();
        other.setId(2L);
        other.setName("Other");
        other.setEmail("other@test.com");
        other.setRole("USER");

        when(registeredUserRepository.findAll()).thenReturn(List.of(mockUser, other));
        // mockUser=1L has 3 reports & 2 routes; user 2L has zero of either (absent rows)
        when(reportRepository.countByCreatedByIdIn(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));
        when(routeRepository.countByCreatedByIdIn(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 2L}));

        List<UserProfileDTO> profiles = registeredUserService.getAllProfiles();

        assertEquals(2, profiles.size());
        UserProfileDTO ada = profiles.stream().filter(p -> p.getId() == 1L).findFirst().orElseThrow();
        UserProfileDTO bob = profiles.stream().filter(p -> p.getId() == 2L).findFirst().orElseThrow();
        assertEquals(3L, ada.getContributionStats().getReportsSubmitted());
        assertEquals(2L, ada.getContributionStats().getRoutesPlanned());
        assertEquals(0L, bob.getContributionStats().getReportsSubmitted());
        assertEquals(0L, bob.getContributionStats().getRoutesPlanned());
        // Public listing must not leak email
        assertNull(ada.getEmail());
        assertNull(bob.getEmail());

        // Each batch query fires exactly once; per-user count queries are never used.
        verify(reportRepository, times(1)).countByCreatedByIdIn(anyCollection());
        verify(routeRepository, times(1)).countByCreatedByIdIn(anyCollection());
        verify(reportRepository, never()).countByCreatedById(any());
        verify(routeRepository, never()).countByCreatedById(any());
    }

    @Test
    void getAllProfiles_emptyUsers_skipsCountQueries() {
        when(registeredUserRepository.findAll()).thenReturn(List.of());

        List<UserProfileDTO> profiles = registeredUserService.getAllProfiles();

        assertTrue(profiles.isEmpty());
        verifyNoInteractions(reportRepository);
        verifyNoInteractions(routeRepository);
    }

    @Test
    void getProfileById_returnsPublicFieldsAndStats_withoutEmail() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(reportRepository.countByCreatedById(1L)).thenReturn(3L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(2L);

        UserProfileDTO dto = registeredUserService.getProfileById(1L);

        assertEquals(1L, dto.getId());
        assertEquals("Test User", dto.getName());
        // Public lookup must NOT expose email — it's PII and only owner-views may include it.
        assertNull(dto.getEmail());
        assertEquals("hello", dto.getBio());
        assertEquals("https://cdn/old.jpg", dto.getAvatarUrl());
        assertEquals("USER", dto.getRole());
        assertEquals(3L, dto.getContributionStats().getReportsSubmitted());
        assertEquals(2L, dto.getContributionStats().getRoutesPlanned());
        verify(reportRepository, times(1)).countByCreatedById(1L);
        verify(routeRepository, times(1)).countByCreatedById(1L);
    }

    @Test
    void getProfileById_throwsAndSkipsCounts_whenUserMissing() {
        when(registeredUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> registeredUserService.getProfileById(99L));

        verifyNoInteractions(reportRepository);
        verifyNoInteractions(routeRepository);
    }

    @Test
    void getProfileById_statsDefaultToZero_noNpe() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UserProfileDTO dto = registeredUserService.getProfileById(1L);

        assertEquals(0L, dto.getContributionStats().getReportsSubmitted());
        assertEquals(0L, dto.getContributionStats().getRoutesPlanned());
    }

    @Test
    void getProfileById_doesNotLeakPassword() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UserProfileDTO dto = registeredUserService.getProfileById(1L);

        // Regression guard: UserProfileDTO must not expose a password field at all.
        for (Field f : dto.getClass().getDeclaredFields()) {
            assertNotEquals("password", f.getName(), "UserProfileDTO must not expose a password field");
        }
    }

    @Test
    void getProfileByEmail_resolvesSameShape() {
        when(registeredUserRepository.findByEmail("test@test.com")).thenReturn(Optional.of(mockUser));
        when(reportRepository.countByCreatedById(1L)).thenReturn(1L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UserProfileDTO dto = registeredUserService.getProfileByEmail("test@test.com");

        assertEquals(1L, dto.getId());
        assertEquals("test@test.com", dto.getEmail());
        assertEquals(1L, dto.getContributionStats().getReportsSubmitted());
    }

    @Test
    void getProfileByEmail_throwsWhenUnknown() {
        when(registeredUserRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> registeredUserService.getProfileByEmail("ghost@example.com"));
    }

    @Test
    void updateProfile_bioOnly_leavesNameUntouched() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UpdateProfileRequest req = new UpdateProfileRequest(null, "new bio");
        UserProfileDTO dto = registeredUserService.updateProfile(1L, req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("Test User", captor.getValue().getName(), "name must be untouched");
        assertEquals("new bio", captor.getValue().getBio());
        assertEquals("new bio", dto.getBio());
    }

    @Test
    void updateProfile_nameOnly_leavesBioUntouched() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UpdateProfileRequest req = new UpdateProfileRequest("Lovelace", null);
        registeredUserService.updateProfile(1L, req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("Lovelace", captor.getValue().getName());
        assertEquals("hello", captor.getValue().getBio(), "bio must be untouched");
    }

    @Test
    void updateProfile_bothFields_persistsBoth() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UpdateProfileRequest req = new UpdateProfileRequest("Lovelace", "new bio");
        registeredUserService.updateProfile(1L, req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("Lovelace", captor.getValue().getName());
        assertEquals("new bio", captor.getValue().getBio());
    }

    @Test
    void updateProfile_emptyBio_clearsBio() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UpdateProfileRequest req = new UpdateProfileRequest(null, "");
        registeredUserService.updateProfile(1L, req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("", captor.getValue().getBio());
    }

    @Test
    void updateProfile_returnsPostSaveDto() {
        RegisteredUser persisted = new RegisteredUser();
        persisted.setId(1L);
        persisted.setName("Persisted-Name");
        persisted.setEmail("test@test.com");
        persisted.setBio("persisted-bio");
        persisted.setRole("USER");

        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenReturn(persisted);
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UpdateProfileRequest req = new UpdateProfileRequest("ignored-by-mock", "ignored-by-mock");
        UserProfileDTO dto = registeredUserService.updateProfile(1L, req);

        assertEquals("Persisted-Name", dto.getName());
        assertEquals("persisted-bio", dto.getBio());
    }

    @Test
    void updateProfile_userMissing_throwsAndDoesNotSave() {
        when(registeredUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> registeredUserService.updateProfile(99L, new UpdateProfileRequest("X", "Y")));

        verify(registeredUserRepository, never()).save(any());
    }

    @Test
    void setAvatar_persistsAndReturnsNewUrl() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        UserProfileDTO dto = registeredUserService.setAvatar(1L, "https://cdn/new.jpg");

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("https://cdn/new.jpg", captor.getValue().getAvatarUrl());
        assertEquals("https://cdn/new.jpg", dto.getAvatarUrl());
    }

    @Test
    void setAvatar_replacesOldValue_doesNotConcatenate() {
        // mockUser starts with "https://cdn/old.jpg"
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(registeredUserRepository.save(any(RegisteredUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByCreatedById(1L)).thenReturn(0L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(0L);

        registeredUserService.setAvatar(1L, "https://cdn/new.jpg");

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals("https://cdn/new.jpg", captor.getValue().getAvatarUrl());
        assertFalse(captor.getValue().getAvatarUrl().contains("old.jpg"));
    }

    @Test
    void setAvatar_userMissing_throws() {
        when(registeredUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> registeredUserService.setAvatar(99L, "https://cdn/new.jpg"));

        verify(registeredUserRepository, never()).save(any());
    }

    @Test
    void loginUser_includesProfileWithStats() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(1L, "test@test.com", "USER")).thenReturn("jwt-token");
        when(reportRepository.countByCreatedById(1L)).thenReturn(4L);
        when(routeRepository.countByCreatedById(1L)).thenReturn(1L);

        LoginResponse resp = registeredUserService.loginUser(validLoginRequest);

        assertEquals("jwt-token", resp.getToken());
        assertNotNull(resp.getProfile());
        assertEquals(1L, resp.getProfile().getId());
        assertEquals("test@test.com", resp.getProfile().getEmail());
        assertEquals(4L, resp.getProfile().getContributionStats().getReportsSubmitted());
        assertEquals(1L, resp.getProfile().getContributionStats().getRoutesPlanned());
    }

    @Test
    void loginUser_badCredentials_skipsProfile() {
        when(registeredUserRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> registeredUserService.loginUser(validLoginRequest));

        verify(reportRepository, never()).countByCreatedById(any());
        verify(routeRepository, never()).countByCreatedById(any());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }
}
