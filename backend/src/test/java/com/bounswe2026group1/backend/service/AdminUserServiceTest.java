package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminChangeRoleRequest;
import com.bounswe2026group1.backend.dto.admin.AdminCreateUserRequest;
import com.bounswe2026group1.backend.dto.admin.AdminUserResponse;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private RegisteredUserRepository userRepository;

    @Mock
    private RegisteredUserService registeredUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;

    private RegisteredUser adminUser;
    private RegisteredUser regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new RegisteredUser(1L, "Admin", "admin@test.com", "hash", UserRole.ADMIN,
                UserStatus.ACTIVE, null, 0, null, null, null, null, null, null, false);
        regularUser = new RegisteredUser(2L, "User", "user@test.com", "hash", UserRole.USER,
                UserStatus.ACTIVE, null, 0, null, null, null, null, null, null, false);
    }

    // --- listUsers ---

    @Test
    void listUsers_NoFilter_ReturnsAllUsers() {
        Page<RegisteredUser> page = new PageImpl<>(List.of(adminUser, regularUser));
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<AdminUserResponse> result = adminUserService.listUsers(null, null, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
    }

    @Test
    void listUsers_FilterByStatus_DelegatesCorrectly() {
        Page<RegisteredUser> page = new PageImpl<>(List.of(regularUser));
        when(userRepository.findByStatus(UserStatus.ACTIVE, PageRequest.of(0, 20))).thenReturn(page);

        Page<AdminUserResponse> result = adminUserService.listUsers(UserStatus.ACTIVE, null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("user@test.com", result.getContent().get(0).getEmail());
    }

    @Test
    void listUsers_FilterByRole_DelegatesCorrectly() {
        Page<RegisteredUser> page = new PageImpl<>(List.of(adminUser));
        when(userRepository.findByRole(UserRole.ADMIN, PageRequest.of(0, 20))).thenReturn(page);

        Page<AdminUserResponse> result = adminUserService.listUsers(null, UserRole.ADMIN, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("admin@test.com", result.getContent().get(0).getEmail());
    }

    @Test
    void listUsers_FilterByStatusAndRole_DelegatesCorrectly() {
        Page<RegisteredUser> page = new PageImpl<>(List.of(adminUser));
        when(userRepository.findByStatusAndRole(UserStatus.ACTIVE, UserRole.ADMIN, PageRequest.of(0, 20))).thenReturn(page);

        Page<AdminUserResponse> result = adminUserService.listUsers(UserStatus.ACTIVE, UserRole.ADMIN, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
    }

    // --- ban ---

    @Test
    void ban_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));

        adminUserService.ban(2L, "admin@test.com");

        assertEquals(UserStatus.BANNED, regularUser.getStatus());
        verify(userRepository).save(regularUser);
    }

    @Test
    void ban_Fail_SelfBan() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.ban(1L, "admin@test.com"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void ban_Fail_UserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.ban(99L, "admin@test.com"));

        assertEquals(404, ex.getStatusCode().value());
    }

    // --- unban ---

    @Test
    void unban_Success() {
        regularUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));

        adminUserService.unban(2L);

        assertEquals(UserStatus.ACTIVE, regularUser.getStatus());
        verify(userRepository).save(regularUser);
    }

    // --- changeRole ---

    @Test
    void changeRole_UserToAdmin_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(regularUser)).thenReturn(regularUser);

        AdminChangeRoleRequest req = new AdminChangeRoleRequest();
        req.setRole(UserRole.ADMIN);

        AdminUserResponse result = adminUserService.changeRole(2L, req, "admin@test.com");

        assertEquals(UserRole.ADMIN, regularUser.getRole());
        assertNotNull(result);
    }

    @Test
    void changeRole_Fail_SelfDemotion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        AdminChangeRoleRequest req = new AdminChangeRoleRequest();
        req.setRole(UserRole.USER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.changeRole(1L, req, "admin@test.com"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void changeRole_Fail_LastAdmin() {
        RegisteredUser anotherAdmin = new RegisteredUser(3L, "Admin2", "admin2@test.com", "hash", UserRole.ADMIN,
                UserStatus.ACTIVE, null, 0, null, null, null, null, null, null, false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(anotherAdmin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        AdminChangeRoleRequest req = new AdminChangeRoleRequest();
        req.setRole(UserRole.USER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.changeRole(3L, req, "admin@test.com"));

        assertEquals(409, ex.getStatusCode().value());
    }

    // --- createUser ---

    @Test
    void createUser_Success() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        RegisteredUser saved = new RegisteredUser(5L, "New", "new@test.com", "encoded", UserRole.USER,
                UserStatus.ACTIVE, null, 0, null, null, null, null, null, null, false);
        when(userRepository.save(any())).thenReturn(saved);

        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setEmail("new@test.com");
        req.setName("New");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.USER);

        AdminUserResponse result = adminUserService.createUser(req);

        assertEquals("new@test.com", result.getEmail());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
    }

    @Test
    void createUser_Fail_DuplicateEmail() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setEmail("user@test.com");
        req.setName("User");
        req.setPassword("Pass@1234");
        req.setRole(UserRole.USER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.createUser(req));

        assertEquals(409, ex.getStatusCode().value());
    }

    // --- deleteUser ---

    @Test
    void deleteUser_Success_DelegatesAnonymization() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));

        adminUserService.deleteUser(2L, "admin@test.com");

        verify(registeredUserService).anonymizeUser(regularUser);
    }

    @Test
    void deleteUser_Fail_SelfDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.deleteUser(1L, "admin@test.com"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void deleteUser_Fail_LastAdmin() {
        RegisteredUser anotherAdmin = new RegisteredUser(3L, "Admin2", "admin2@test.com", "hash", UserRole.ADMIN,
                UserStatus.ACTIVE, null, 0, null, null, null, null, null, null, false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(anotherAdmin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.deleteUser(3L, "admin@test.com"));

        assertEquals(409, ex.getStatusCode().value());
    }
}
