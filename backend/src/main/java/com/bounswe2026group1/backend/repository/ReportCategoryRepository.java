package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.ReportCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportCategoryRepository extends JpaRepository<ReportCategory, Long> {

    List<ReportCategory> findByParentIsNull();

    boolean existsByParentId(Long parentId);
}
