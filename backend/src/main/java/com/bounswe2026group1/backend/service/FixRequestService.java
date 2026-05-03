package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.model.FixRequest;
import com.bounswe2026group1.backend.model.FixRequestMedia;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.FixRequestVote;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.FixRequestRepository;
import com.bounswe2026group1.backend.repository.FixRequestVoteRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.util.TransactionalEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixRequestService {

    private final FixRequestRepository fixRequestRepository;
    private final FixRequestVoteRepository fixRequestVoteRepository;
    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final S3MediaService s3MediaService;
    private final PublicSseService publicSseService;

    @Value("${app.report.fix.threshold:5}")
    private int fixThreshold;

    @Value("${app.report.fix.ratio:0.60}")
    private double fixRatio;

    private static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Transactional
    public FixRequestResponse submit(Long reportId, String email, String description, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one media file is required");
        }
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        // Pre-validate every file before any S3 upload, so a single bad file in a batch
        // doesn't leave a partial set of uploaded objects on S3 that the rolled-back
        // transaction won't clean up.
        try {
            for (MultipartFile file : files) {
                s3MediaService.validate(file);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        RegisteredUser submitter = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + reportId));

        fixRequestRepository.findFirstByReportReportIdAndState(reportId, FixRequestState.OPEN)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "An open fix request already exists for this report");
                });

        FixRequest fixRequest = new FixRequest(report, submitter, description);

        for (MultipartFile file : files) {
            String url = s3MediaService.uploadFile(file);
            fixRequest.getMediaList().add(new FixRequestMedia(fixRequest, url));
        }

        FixRequest saved = fixRequestRepository.save(fixRequest);
        TransactionalEvents.runAfterCommit(() -> publicSseService.broadcastFixRequested(report));
        return FixRequestResponse.fromEntity(saved);
    }

    @Transactional
    public FixRequestResponse agree(Long fixRequestId, String email) {
        return castVote(fixRequestId, email, VoteType.AGREE);
    }

    @Transactional
    public FixRequestResponse disagree(Long fixRequestId, String email) {
        return castVote(fixRequestId, email, VoteType.DISAGREE);
    }

    private FixRequestResponse castVote(Long fixRequestId, String email, VoteType desired) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        FixRequest fixRequest = fixRequestRepository.findById(fixRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fix request not found with id: " + fixRequestId));

        if (fixRequest.getState() != FixRequestState.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fix request is not open for voting");
        }

        var existing = fixRequestVoteRepository.findByUserIdAndFixRequestId(user.getId(), fixRequestId);

        if (existing.isPresent()) {
            FixRequestVote vote = existing.get();
            if (vote.getVoteType() == desired) {
                // Toggle off
                if (desired == VoteType.AGREE) fixRequest.decrementAgrees();
                else fixRequest.decrementDisagrees();
                fixRequestVoteRepository.delete(vote);
            } else {
                // Switch
                if (desired == VoteType.AGREE) {
                    fixRequest.decrementDisagrees();
                    fixRequest.incrementAgrees();
                } else {
                    fixRequest.decrementAgrees();
                    fixRequest.incrementDisagrees();
                }
                vote.setVoteType(desired);
                fixRequestVoteRepository.save(vote);
            }
        } else {
            if (desired == VoteType.AGREE) fixRequest.incrementAgrees();
            else fixRequest.incrementDisagrees();
            fixRequestVoteRepository.save(new FixRequestVote(user, fixRequest, desired));
        }

        evaluateFixStatus(fixRequest);
        FixRequest saved = fixRequestRepository.save(fixRequest);
        VoteType callerVote = fixRequestVoteRepository.findByUserIdAndFixRequestId(user.getId(), fixRequestId)
                .map(FixRequestVote::getVoteType)
                .orElse(null);
        return FixRequestResponse.fromEntity(saved, callerVote);
    }

    /**
     * If the fix request has crossed the count and ratio thresholds, transition the parent
     * report to FIXED and broadcast the fixed event after commit. No-op otherwise.
     */
    private void evaluateFixStatus(FixRequest fixRequest) {
        if (fixRequest.getState() != FixRequestState.OPEN) return;
        int total = fixRequest.getAgrees() + fixRequest.getDisagrees();
        if (fixRequest.getAgrees() < fixThreshold) return;
        if (total == 0) return;
        double ratio = (double) fixRequest.getAgrees() / total;
        if (ratio < fixRatio) return;

        fixRequest.setState(FixRequestState.RESOLVED_FIXED);
        fixRequest.setResolvedAt(Instant.now());

        Report report = fixRequest.getReport();
        report.setStatus(ReportStatus.FIXED);
        report.setFixedAt(Instant.now());
        reportRepository.save(report);

        TransactionalEvents.runAfterCommit(() -> publicSseService.broadcastFixed(report));
    }

    /**
     * Marks any OPEN fix request older than the given window as EXPIRED.
     * Called by ReportLifecycleScheduler.
     */
    @Transactional
    public int expireOpenFixRequestsOlderThan(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        List<FixRequest> stale = fixRequestRepository.findByStateAndCreatedAtBefore(FixRequestState.OPEN, cutoff);
        Instant now = Instant.now();
        for (FixRequest fr : stale) {
            fr.setState(FixRequestState.EXPIRED);
            fr.setResolvedAt(now);
        }
        fixRequestRepository.saveAll(stale);
        return stale.size();
    }

    /**
     * Deletes Reports that have been in FIXED state longer than the configured retention,
     * along with their media (S3 + DB) and any cascaded child rows. Broadcasts
     * report-deleted SSE for each removed report after the surrounding transaction commits.
     */
    @Transactional
    public int deleteFixedReportsOlderThan(Duration retention) {
        Instant cutoff = Instant.now().minus(retention);
        List<Report> stale = reportRepository.findByStatusAndFixedAtBefore(ReportStatus.FIXED, cutoff);
        for (Report report : stale) {
            report.getMediaList().forEach(m -> s3MediaService.deleteFile(m.getFilePath()));
            report.getFixRequests().forEach(fr ->
                    fr.getMediaList().forEach(frm -> s3MediaService.deleteFile(frm.getFilePath())));
            Long deletedId = report.getReportId();
            reportRepository.delete(report);
            TransactionalEvents.runAfterCommit(() -> publicSseService.broadcastReportDeleted(deletedId));
        }
        return stale.size();
    }
}
