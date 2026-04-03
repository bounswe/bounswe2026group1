package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.ReportVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportVerificationRepository extends JpaRepository<ReportVerification, Long> {
    Optional<ReportVerification> findByUserIdAndReportReportId(Long userId, Long reportId);
}
