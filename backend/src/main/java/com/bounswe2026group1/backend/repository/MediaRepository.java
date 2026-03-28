package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {
    List<Media> findByReportReportId(Long reportId);
}
