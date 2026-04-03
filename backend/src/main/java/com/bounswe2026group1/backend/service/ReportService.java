package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.bounswe2026group1.backend.model.ReportStatus;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;

    // Fetched from application.properties
    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

    public List<ReportResponse> getAll() {
        return reportRepository.findAll().stream()
                .map(ReportResponse::fromEntity)
                .toList();
    }

    public Optional<ReportResponse> getById(Long id) {
        return reportRepository.findById(id)
                .map(ReportResponse::fromEntity);
    }

    public List<ReportResponse> getByUserId(Long userId) {
        return reportRepository.findByCreatedById(userId).stream()
                .map(ReportResponse::fromEntity)
                .toList();
    }

    public ReportResponse create(CreateReportRequest request) {
        RegisteredUser user = registeredUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        Location location = new Location(request.getLatitude(), request.getLongitude());
        Report report = new Report(user, location, request.getDescription(), request.getTag());

        Report saved = reportRepository.save(report);
        return ReportResponse.fromEntity(saved);
    }

    public Optional<ReportResponse> update(Long id, CreateReportRequest request) {
        return reportRepository.findById(id).map(existing -> {
            existing.setDescription(request.getDescription());
            existing.setTag(request.getTag());
            existing.getLocation().setLatitude(request.getLatitude());
            existing.getLocation().setLongitude(request.getLongitude());
            Report saved = reportRepository.save(existing);
            return ReportResponse.fromEntity(saved);
        });
    }

    public boolean delete(Long id) {
        if (!reportRepository.existsById(id)) return false;
        reportRepository.deleteById(id);
        return true;
    }

    @Transactional
    public ReportResponse verifyReport(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found with id: " + id));

        report.incrementAgrees();       // Called from Report.java

        // Check if the new agree count hits the threshold and the Report is already verified
        if (report.getAgrees() >= verificationThreshold && report.getStatus() != ReportStatus.VERIFIED) {
            report.setStatus(ReportStatus.VERIFIED);
        }

        // Save to the DB
        Report saved = reportRepository.save(report);
        return ReportResponse.fromEntity(saved);
    }

    @Transactional
    public ReportResponse unverifyReport(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found with id: " + id));

        report.incrementDisagrees();        // Call from the Report.java


        Report saved = reportRepository.save(report);
        return ReportResponse.fromEntity(saved);
    }
}
