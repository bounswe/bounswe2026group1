package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    long countByCreatedById(Long userId);

    List<Route> findByCreatedByIdOrderByIdDesc(Long userId);
}
