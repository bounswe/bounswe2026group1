package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.MediaRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bounswe2026group1.backend.model.ReportStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final MediaRepository mediaRepository;
    private final ReportVerificationRepository verificationRepository;
    private final PublicSseService publicSseService;
    private final S3MediaService s3MediaService;

    // Fetched from application.properties
    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

    public List<ReportResponse> getAll(String email) {
        Long userId = resolveUserId(email);
        List<Report> reports = reportRepository.findAll();
        Map<Long, VoteType> votesByReportId = resolveUserVotes(userId, reports);
        return reports.stream()
                .map(r -> ReportResponse.fromEntity(r, votesByReportId.get(r.getReportId())))
                .toList();
    }

    public Optional<ReportResponse> getById(Long id, String email) {
        Long userId = resolveUserId(email);
        return reportRepository.findById(id)
                .map(r -> ReportResponse.fromEntity(r, resolveUserVote(userId, r.getReportId())));
    }

    public List<ReportResponse> getByUserId(Long userId) {
        return reportRepository.findByCreatedById(userId).stream()
                .map(ReportResponse::fromEntity)
                .toList();
    }

    private Long resolveUserId(String email) {
        if (email == null) return null;
        return registeredUserRepository.findByEmail(email)
                .map(RegisteredUser::getId)
                .orElse(null);
    }

    private VoteType resolveUserVote(Long userId, Long reportId) {
        if (userId == null) return null;
        return verificationRepository.findByUserIdAndReportReportId(userId, reportId)
                .map(ReportVerification::getVoteType)
                .orElse(null);
    }

    private Map<Long, VoteType> resolveUserVotes(Long userId, List<Report> reports) {
        if (userId == null || reports.isEmpty()) return Collections.emptyMap();
        List<Long> reportIds = reports.stream().map(Report::getReportId).toList();
        return verificationRepository.findVotesByUserIdAndReportIds(userId, reportIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (VoteType) row[1]));
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request) {
        RegisteredUser user = registeredUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        Location location = new Location(request.getLatitude(), request.getLongitude());
        Report report = new Report(user, location, request.getDescription(), request.getTag());

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportCreated(saved));
        return ReportResponse.fromEntity(saved);
    }

    @Transactional
    public ReportResponse update(Long id, UpdateReportRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + id));

        report.setDescription(request.getDescription());
        report.setTag(request.getTag());
        report.getLocation().setLatitude(request.getLatitude());
        report.getLocation().setLongitude(request.getLongitude());

        if (request.getMediaIdsToRemove() != null && !request.getMediaIdsToRemove().isEmpty()) {
            List<Media> toRemove = report.getMediaList().stream()
                    .filter(m -> request.getMediaIdsToRemove().contains(m.getId()))
                    .toList();
            toRemove.forEach(m -> s3MediaService.deleteFile(m.getFilePath()));
            report.getMediaList().removeAll(toRemove);
        }

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, "update"));
        return ReportResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(Long id, String email) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + id));

        RegisteredUser requester = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!report.getCreatedBy().getId().equals(requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }

        List<String> mediaPaths = report.getMediaList().stream()
                .map(Media::getFilePath)
                .toList();
        mediaPaths.forEach(s3MediaService::deleteFile);

        reportRepository.delete(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportDeleted(id));
    }

    @Transactional
    public ReportResponse verifyReport(Long id, String email) {
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + id));

        var existing = verificationRepository.findByUserIdAndReportReportId(user.getId(), id);

        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.AGREE) {
                // Toggle off: remove the vote
                report.decrementAgrees();
                verificationRepository.delete(existing.get());
            } else {
                // Switch from DISAGREE to AGREE
                report.decrementDisagrees();
                report.incrementAgrees();
                existing.get().setVoteType(VoteType.AGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            // First vote
            report.incrementAgrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.AGREE));
        }

        if (report.getAgrees() >= verificationThreshold && report.getStatus() != ReportStatus.VERIFIED) {
            report.setStatus(ReportStatus.VERIFIED);
        }
        if (report.getAgrees() < verificationThreshold && report.getStatus() == ReportStatus.VERIFIED) {
            report.setStatus(ReportStatus.PENDING);
        }

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, "verify"));
        return ReportResponse.fromEntity(saved, resolveUserVote(user.getId(), saved.getReportId()));
    }

    @Transactional
    public ReportResponse unverifyReport(Long id, String email) {
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + id));

        var existing = verificationRepository.findByUserIdAndReportReportId(user.getId(), id);

        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.DISAGREE) {
                // Toggle off: remove the vote
                report.decrementDisagrees();
                verificationRepository.delete(existing.get());
            } else {
                // Switch from AGREE to DISAGREE
                report.decrementAgrees();
                report.incrementDisagrees();
                existing.get().setVoteType(VoteType.DISAGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            // First vote
            report.incrementDisagrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.DISAGREE));
        }

        if (report.getAgrees() < verificationThreshold && report.getStatus() == ReportStatus.VERIFIED) {
            report.setStatus(ReportStatus.PENDING);
        }

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, "unverify"));
        return ReportResponse.fromEntity(saved, resolveUserVote(user.getId(), saved.getReportId()));
    }

    public void addMediaToReport(Long reportId, String mediaUrl) {
        // Try to find report by sent reportId, if not throw a NoSuchElement exception
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));

        // Creates the Media entity
        Media media = new Media();
        // Foreign key relation
        media.setReport(report);
        // Saves the actual public URL which is sent by MediaController
        media.setFilePath(mediaUrl);
        // Saves the created Media entity to the database
        Media savedMedia = mediaRepository.save(media);
        broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
    }

    private void broadcastAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
