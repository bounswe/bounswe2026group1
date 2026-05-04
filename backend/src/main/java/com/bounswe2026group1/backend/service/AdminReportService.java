package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.ReportRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final S3MediaService s3MediaService;
    private final PublicSseService publicSseService;

    public Page<ReportResponse> listReports(ReportStatus status, Long categoryId,
                                            ReportEnvironment environment, ReportType type,
                                            Instant dateFrom, Instant dateTo,
                                            Pageable pageable) {
        Specification<Report> spec = buildSpec(status, categoryId, environment, type, dateFrom, dateTo);
        return reportRepository.findAll(spec, pageable)
                .map(r -> ReportResponse.fromEntity(r, null));
    }

    @Transactional
    public void deleteReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found with id: " + reportId));

        report.getMediaList().stream().map(Media::getFilePath).forEach(s3MediaService::deleteFile);
        reportRepository.delete(report);
        publicSseService.broadcastReportDeleted(reportId);
    }

    // -------------------------------------------------------------------------

    private Specification<Report> buildSpec(ReportStatus status, Long categoryId,
                                            ReportEnvironment environment, ReportType type,
                                            Instant dateFrom, Instant dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (environment != null) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("category").get("type"), type));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("publishDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("publishDate"), dateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
