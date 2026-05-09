package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegisteredUserRepository extends JpaRepository<RegisteredUser, Long>,
        JpaSpecificationExecutor<RegisteredUser> {

    boolean existsByEmail(String email);
    Optional<RegisteredUser> findByEmail(String email);

    // Filtering for admin user list
    Page<RegisteredUser> findByStatusAndRole(UserStatus status, UserRole role, Pageable pageable);
    Page<RegisteredUser> findByStatus(UserStatus status, Pageable pageable);
    Page<RegisteredUser> findByRole(UserRole role, Pageable pageable);

    // Used to enforce the "at least one admin" rule
    long countByRole(UserRole role);
    long countByStatus(UserStatus status);

    // User search (#306 / #501): case-insensitive substring match across name OR email.
    Page<RegisteredUser> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);
}
