package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegisteredUserRepository extends JpaRepository<RegisteredUser, Long> {
    boolean existsByEmail(String email);
    java.util.Optional<RegisteredUser> findByEmail(String email);

    Page<RegisteredUser> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
