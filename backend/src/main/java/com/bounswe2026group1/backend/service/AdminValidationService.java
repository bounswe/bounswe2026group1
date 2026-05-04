package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminValidationResponse;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminValidationService {

    private final ReportVerificationRepository verificationRepository;
    private final ReportRepository reportRepository;

    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

    public Page<AdminValidationResponse> listValidations(Long reportId, Long userId,
                                                         VoteType voteType, Pageable pageable) {
        Specification<ReportVerification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reportId != null) {
                predicates.add(cb.equal(root.get("report").get("reportId"), reportId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (voteType != null) {
                predicates.add(cb.equal(root.get("voteType"), voteType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return verificationRepository.findAll(spec, pageable)
                .map(AdminValidationResponse::fromEntity);
    }

    @Transactional
    public void deleteValidation(Long verificationId) {
        ReportVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Verification not found with id: " + verificationId));

        Report report = verification.getReport();

        // Adjust counter based on vote type
        if (verification.getVoteType() == VoteType.AGREE) {
            report.decrementAgrees();
        } else {
            report.decrementDisagrees();
        }

        // Re-evaluate status transitions
        if (report.getAgrees() < verificationThreshold && report.getStatus() == ReportStatus.VERIFIED) {
            report.setStatus(ReportStatus.PENDING);
        }

        verificationRepository.delete(verification);
        reportRepository.save(report);
    }
}
