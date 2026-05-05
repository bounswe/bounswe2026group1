package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RegisteredUserRepository extends JpaRepository<RegisteredUser, Long> {
    boolean existsByEmail(String email);
    java.util.Optional<RegisteredUser> findByEmail(String email);

    /**
     * Case-insensitive name search ordered by relevance:
     * names that START WITH the query come before names that merely CONTAIN it,
     * with each group sorted alphabetically (case-insensitive) and id as a
     * deterministic tiebreaker. An empty query matches every user.
     */
    @Query(value = """
            SELECT u FROM RegisteredUser u
            WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '!'
            ORDER BY
                CASE WHEN LOWER(u.name) LIKE LOWER(CONCAT(:q, '%')) ESCAPE '!' THEN 0 ELSE 1 END,
                LOWER(u.name) ASC,
                u.id ASC
            """,
            countQuery = """
            SELECT COUNT(u) FROM RegisteredUser u
            WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '!'
            """)
    Page<RegisteredUser> searchByName(@Param("q") String q, Pageable pageable);
}
