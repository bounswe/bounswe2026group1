package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final MediaRepository mediaRepository;

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
    public void addMediaToReport(Long reportId, String mediaUrl) {
        // Try to find report by sent reportId, if not throw a Runtime exception
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found with id: " + reportId));

        // Creates the Media entity
        Media media = new Media();
        // Foreign key relation
        media.setReport(report);
        // Saves the actual public URL which is sent by MediaController
        media.setFilePath(mediaUrl);
        // Saves the created Media entity to the database
        mediaRepository.save(media);
    }
}
