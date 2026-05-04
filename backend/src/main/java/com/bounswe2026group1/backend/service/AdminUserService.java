package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminChangeRoleRequest;
import com.bounswe2026group1.backend.dto.admin.AdminCreateUserRequest;
import com.bounswe2026group1.backend.dto.admin.AdminUserResponse;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final RegisteredUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<AdminUserResponse> listUsers(UserStatus status, UserRole role, Pageable pageable) {
        Page<RegisteredUser> page;
        if (status != null && role != null) {
            page = userRepository.findByStatusAndRole(status, role, pageable);
        } else if (status != null) {
            page = userRepository.findByStatus(status, pageable);
        } else if (role != null) {
            page = userRepository.findByRole(role, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }
        return page.map(AdminUserResponse::fromEntity);
    }

    @Transactional
    public void ban(Long targetId, String adminEmail) {
        RegisteredUser target = findUser(targetId);
        guardSelfAction(target, adminEmail, "ban yourself");
        target.setStatus(UserStatus.BANNED);
        userRepository.save(target);
    }

    @Transactional
    public void unban(Long targetId) {
        RegisteredUser target = findUser(targetId);
        target.setStatus(UserStatus.ACTIVE);
        userRepository.save(target);
    }

    @Transactional
    public AdminUserResponse changeRole(Long targetId, AdminChangeRoleRequest request, String adminEmail) {
        UserRole newRole = request.getRole();

        RegisteredUser target = findUser(targetId);
        guardSelfAction(target, adminEmail, "change your own role");

        // Prevent demoting the last admin
        if (newRole == UserRole.USER && target.getRole() == UserRole.ADMIN) {
            if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot demote the last admin");
            }
        }

        target.setRole(newRole);
        return AdminUserResponse.fromEntity(userRepository.save(target));
    }

    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        RegisteredUser user = new RegisteredUser();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);
        user.setPoints(0);

        return AdminUserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long targetId, String adminEmail) {
        RegisteredUser target = findUser(targetId);
        guardSelfAction(target, adminEmail, "delete your own account");

        // Prevent deleting the last admin
        if (target.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete the last admin");
        }

        // Anonymize PII in-place so that FK references from reports/comments/verifications stay valid
        target.setName("Deleted User");
        target.setEmail("deleted_" + targetId + "@deleted.invalid");
        target.setPassword("$DELETED$");
        target.setStatus(UserStatus.BANNED);
        userRepository.save(target);
    }

    private RegisteredUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + id));
    }

    private void guardSelfAction(RegisteredUser target, String adminEmail, String action) {
        if (target.getEmail().equals(adminEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admins cannot " + action);
        }
    }
}
