package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // User search (#306 / #501): case-insensitive substring match across name OR email, regular users only.
    @Query("SELECT u FROM RegisteredUser u WHERE u.role = com.bounswe2026group1.backend.model.UserRole.USER " +
           "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<RegisteredUser> searchRegularUsersByNameOrEmail(@Param("q") String q, Pageable pageable);
}
