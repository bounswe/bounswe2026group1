package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    long countByCreatedById(Long userId);

    /** Batch variant of {@link #countByCreatedById}: one query, grouped by user.
     *  Returns rows of {@code [userId, count]}; absent users mean zero. */
    @Query("""
            SELECT r.createdBy.id, COUNT(r) FROM Route r
            WHERE r.createdBy.id IN :userIds
            GROUP BY r.createdBy.id
            """)
    List<Object[]> countByCreatedByIdIn(@Param("userIds") Collection<Long> userIds);

    List<Route> findByCreatedByIdOrderByIdDesc(Long userId);
}
