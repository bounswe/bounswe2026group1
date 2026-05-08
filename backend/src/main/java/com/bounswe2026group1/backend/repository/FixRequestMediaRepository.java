package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.FixRequestMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixRequestMediaRepository extends JpaRepository<FixRequestMedia, Long> {
}
