package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminValidationService {

    private final ReportVerificationRepository verificationRepository;
    private final ReportRepository reportRepository;

    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

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
