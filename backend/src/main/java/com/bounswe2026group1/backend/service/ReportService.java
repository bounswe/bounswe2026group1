package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;

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
}
