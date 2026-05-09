package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.UserCustomRoutingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCustomRoutingProfileRepository extends JpaRepository<UserCustomRoutingProfile, Long> {

    List<UserCustomRoutingProfile> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<UserCustomRoutingProfile> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
