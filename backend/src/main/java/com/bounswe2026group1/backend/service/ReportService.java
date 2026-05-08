package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.*;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final MediaRepository mediaRepository;
    private final ReportVerificationRepository verificationRepository;
    private final FixRequestRepository fixRequestRepository;
    private final FixRequestVoteRepository fixRequestVoteRepository;
    private final PublicSseService publicSseService;
    private final NotificationService notificationService;
    private final S3MediaService s3MediaService;
    private final MeasurementValidator measurementValidator;
    private final OverpassService overpassService;

    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

    @Value("${app.report.verification.ratio:0.60}")
    private double verificationRatio;

    public List<ReportResponse> getAll(String email) {
        Long userId = resolveUserId(email);
        List<Report> reports = reportRepository.findAll();
        Map<Long, VoteType> votesByReportId = resolveUserVotes(userId, reports);
        Map<Long, FixRequestResponse> activeFixByReportId = resolveActiveFixRequests(userId, reports);
        return reports.stream()
                .map(r -> ReportResponse.fromEntity(
                        r,
                        votesByReportId.get(r.getReportId()),
                        buildObjectResponses(r),
                        activeFixByReportId.get(r.getReportId())))
                .toList();
    }

    public Optional<ReportResponse> getById(Long id, String email) {
        Long userId = resolveUserId(email);
        return reportRepository.findById(id)
                .map(r -> ReportResponse.fromEntity(
                        r,
                        resolveUserVote(userId, r.getReportId()),
                        buildObjectResponses(r),
                        resolveActiveFixRequest(userId, r.getReportId())));
    }

    public List<ReportResponse> getByUserId(Long userId) {
        return reportRepository.findByCreatedById(userId).stream()
                .map(r -> ReportResponse.fromEntity(
                        r,
                        resolveUserVote(userId, r.getReportId()),
                        buildObjectResponses(r),
                        resolveActiveFixRequest(userId, r.getReportId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> feed(
            ReportType reportType,
            ReportEnvironment environment,
            Double latitude,
            Double longitude,
            Pageable pageable,
            String email) {

        if (latitude != null ^ longitude != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude and longitude must both be provided for proximity feed");
        }

        Pageable paged = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(100, Math.max(1, pageable.getPageSize())));

        Long userId = resolveUserId(email);

        Page<Report> page = latitude != null
                ? reportRepository.findFeedWithinRadius(reportType, environment, latitude, longitude, paged)
                : reportRepository.findFeedRecent(reportType, environment, paged);

        Map<Long, VoteType> votesByReportId = resolveUserVotes(userId, page.getContent());
        Map<Long, FixRequestResponse> activeFixByReportId = resolveActiveFixRequests(userId, page.getContent());
        List<ReportResponse> responses = page.getContent().stream()
                .map(r -> ReportResponse.fromEntity(
                        r,
                        votesByReportId.get(r.getReportId()),
                        buildObjectResponses(r),
                        activeFixByReportId.get(r.getReportId())))
                .toList();
        return new PageImpl<>(responses, paged, page.getTotalElements());
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request) {
        RegisteredUser user = registeredUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + request.getUserId()));

        if (request.getReportType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportType is required");
        }

        Location reportedPoint = new Location(request.getLatitude(), request.getLongitude());
        Point reportLocation = GeoUtils.point4326(request.getLatitude(), request.getLongitude());
        Report report = new Report(user, reportLocation, request.getDescription(),
                request.getReportType(), request.getEnvironment());

        List<ReportObjectRequest> objectRequests = request.getObjects() != null
                ? request.getObjects() : List.of();

        for (ReportObjectRequest objReq : objectRequests) {
            validateIssues(objReq);
        }

        // FEATURE reports: snap to nearest stair using the first object's type
        if (request.getReportType() == ReportType.FEATURE && !objectRequests.isEmpty()
                && objectRequests.get(0).getObjectType() == ObjectType.RAMP) {
            try {
                Location[] endpoints = overpassService.snapToNearestStair(reportedPoint);
                report.setEntryPoint(endpoints[0]);
                report.setExitPoint(endpoints[1]);
            } catch (RoutingException e) {
                throw e;
            } catch (Exception e) {
                throw new RoutingException(HttpStatus.BAD_REQUEST,
                        "Could not verify stair location. Please try again later.");
            }
        }

        Report saved = reportRepository.save(report);

        for (ReportObjectRequest objReq : objectRequests) {
            Set<IssueType> issues = objReq.getIssues() != null
                    ? new HashSet<>(objReq.getIssues()) : new HashSet<>();
            ReportObject obj = new ReportObject(saved, objReq.getObjectType(), issues, objReq.getMeasurements());
            saved.getObjects().add(obj);
        }

        saved = reportRepository.save(saved);
        Report finalSaved = saved;
        broadcastAfterCommit(() -> publicSseService.broadcastReportCreated(finalSaved));

        return ReportResponse.fromEntity(saved, null, buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
    }

    @Transactional
    public ReportResponse update(Long id, UpdateReportRequest request, String editorEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found with id: " + id));

        RegisteredUser editor = registeredUserRepository.findByEmail(editorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Map<String, Object[]> diff = new LinkedHashMap<>();

        if (request.getDescription() != null && !request.getDescription().equals(report.getDescription())) {
            diff.put("description", new Object[]{report.getDescription(), request.getDescription()});
            report.setDescription(request.getDescription());
        }

        if (request.getEnvironment() != null && request.getEnvironment() != report.getEnvironment()) {
            diff.put("environment", new Object[]{report.getEnvironment(), request.getEnvironment()});
            report.setEnvironment(request.getEnvironment());
        }

        if (request.getLatitude() != null && Math.abs(request.getLatitude() - report.getLocation().getY()) > 1e-9) {
            diff.put("latitude", new Object[]{report.getLocation().getY(), request.getLatitude()});
            report.setLocation(GeoUtils.point4326(request.getLatitude(), report.getLocation().getX()));
        }
        if (request.getLongitude() != null && Math.abs(request.getLongitude() - report.getLocation().getX()) > 1e-9) {
            diff.put("longitude", new Object[]{report.getLocation().getX(), request.getLongitude()});
            report.setLocation(GeoUtils.point4326(report.getLocation().getY(), request.getLongitude()));
        }

        if (request.getObjects() != null) {
            for (ReportObjectRequest objReq : request.getObjects()) {
                validateIssues(objReq);
            }
            diff.put("objects", new Object[]{"<previous>", "<updated>"});
            report.getObjects().clear();
            for (ReportObjectRequest objReq : request.getObjects()) {
                Set<IssueType> issues = objReq.getIssues() != null
                        ? new HashSet<>(objReq.getIssues()) : new HashSet<>();
                report.getObjects().add(new ReportObject(report, objReq.getObjectType(), issues, objReq.getMeasurements()));
            }
        }

        if (request.getMediaIdsToRemove() != null && !request.getMediaIdsToRemove().isEmpty()) {
            List<Media> toRemove = report.getMediaList().stream()
                    .filter(m -> request.getMediaIdsToRemove().contains(m.getId()))
                    .toList();
            toRemove.forEach(m -> s3MediaService.deleteFile(m.getFilePath()));
            report.getMediaList().removeAll(toRemove);
        }

        if (!diff.isEmpty()) {
            appendEditHistory(report, editor.getId(), diff);
            report.setLastEditedBy(editor);
        }

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, "update"));

        return ReportResponse.fromEntity(saved,
                resolveUserVote(editor.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(editor.getId(), saved.getReportId()));
    }

    @Transactional
    public void delete(Long id, String email) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found with id: " + id));

        RegisteredUser requester = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!report.getCreatedBy().getId().equals(requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }

        report.getMediaList().stream().map(Media::getFilePath).forEach(s3MediaService::deleteFile);
        report.getFixRequests().forEach(fr ->
                fr.getMediaList().forEach(frm -> s3MediaService.deleteFile(frm.getFilePath())));
        reportRepository.delete(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportDeleted(id));
    }

    @Transactional
    public ReportResponse verifyReport(Long id, String email) {
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + id));

        if (report.getStatus() == ReportStatus.FIXED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is already fixed");
        }

        var existing = verificationRepository.findByUserIdAndReportReportId(user.getId(), id);

        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.AGREE) {
                report.decrementAgrees();
                verificationRepository.delete(existing.get());
            } else {
                report.decrementDisagrees();
                report.incrementAgrees();
                existing.get().setVoteType(VoteType.AGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            report.incrementAgrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.AGREE));
        }

        ReportStatus previousStatus = report.getStatus();
        ReportStatus newStatus = evaluateStatus(report);

        Report saved = reportRepository.save(report);
        String op = switch (newStatus) { case VERIFIED -> "verify"; case REJECTED -> "reject"; default -> "pending"; };
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, op));
        if (newStatus != previousStatus) {
            notificationService.notifyStatusChange(saved, user.getId());
        }
        return ReportResponse.fromEntity(saved,
                resolveUserVote(user.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
    }

    @Transactional
    public ReportResponse unverifyReport(Long id, String email) {
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + id));

        if (report.getStatus() == ReportStatus.FIXED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is already fixed");
        }

        var existing = verificationRepository.findByUserIdAndReportReportId(user.getId(), id);

        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.DISAGREE) {
                report.decrementDisagrees();
                verificationRepository.delete(existing.get());
            } else {
                report.decrementAgrees();
                report.incrementDisagrees();
                existing.get().setVoteType(VoteType.DISAGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            report.incrementDisagrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.DISAGREE));
        }

        ReportStatus previousStatus = report.getStatus();
        ReportStatus newStatus = evaluateStatus(report);

        Report saved = reportRepository.save(report);
        String op = switch (newStatus) { case VERIFIED -> "verify"; case REJECTED -> "reject"; default -> "pending"; };
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, op));
        if (newStatus != previousStatus) {
            notificationService.notifyStatusChange(saved, user.getId());
        }
        return ReportResponse.fromEntity(saved,
                resolveUserVote(user.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
    }

    private ReportStatus evaluateStatus(Report report) {
        int agrees = report.getAgrees();
        int disagrees = report.getDisagrees();
        int total = agrees + disagrees;
        if (total == 0) return report.getStatus();

        double agreeRatio = (double) agrees / total;
        double disagreeRatio = (double) disagrees / total;
        ReportStatus current = report.getStatus();
        ReportStatus target = current;

        if (agrees >= verificationThreshold && agreeRatio >= verificationRatio) {
            target = ReportStatus.VERIFIED;
        } else if (disagrees >= verificationThreshold && disagreeRatio >= verificationRatio) {
            target = ReportStatus.REJECTED;
        } else if (current == ReportStatus.VERIFIED
                && disagrees >= verificationThreshold
                && agreeRatio < verificationRatio) {
            target = ReportStatus.PENDING;
        } else if (current == ReportStatus.REJECTED
                && agrees >= verificationThreshold
                && disagreeRatio < verificationRatio) {
            target = ReportStatus.PENDING;
        }

        if (target != current) {
            report.setStatus(target);
        }
        return target;
    }

    public void addMediaToReport(Long reportId, String mediaUrl) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));
        Media media = new Media();
        media.setReport(report);
        media.setFilePath(mediaUrl);
        Media savedMedia = mediaRepository.save(media);
        broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
    }

    @Transactional
    public void addMediaToReportBatch(Long reportId, List<String> mediaUrls) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));
        for (String mediaUrl : mediaUrls) {
            Media media = new Media();
            media.setReport(report);
            media.setFilePath(mediaUrl);
            Media savedMedia = mediaRepository.save(media);
            broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateIssues(ReportObjectRequest objReq) {
        if (objReq.getObjectType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectType is required for each object");
        }
        if (objReq.getIssues() != null) {
            for (IssueType issue : objReq.getIssues()) {
                if (!issue.isValidFor(objReq.getObjectType())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Issue " + issue + " is not valid for object type " + objReq.getObjectType());
                }
            }
        }
    }

    private List<ReportObjectResponse> buildObjectResponses(Report report) {
        return report.getObjects().stream()
                .map(obj -> {
                    List<MeasurementWarning> warnings = List.of();
                    String schemaJson = MeasurementSchemas.getSchemaJson(obj.getObjectType());
                    if (obj.getMeasurements() != null && schemaJson != null) {
                        warnings = measurementValidator.validate(obj.getMeasurements(), schemaJson);
                    }
                    return ReportObjectResponse.fromEntity(obj, warnings);
                })
                .toList();
    }

    private void appendEditHistory(Report report, Long editorId, Map<String, Object[]> diff) {
        try {
            ArrayNode history = report.getEditHistory() != null
                    ? (ArrayNode) MAPPER.readTree(report.getEditHistory())
                    : MAPPER.createArrayNode();

            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("editedBy", editorId);
            entry.put("editedAt", Instant.now().toString());

            ObjectNode diffNode = MAPPER.createObjectNode();
            for (Map.Entry<String, Object[]> e : diff.entrySet()) {
                ArrayNode pair = MAPPER.createArrayNode();
                pair.addPOJO(e.getValue()[0]);
                pair.addPOJO(e.getValue()[1]);
                diffNode.set(e.getKey(), pair);
            }
            entry.set("diff", diffNode);
            history.add(entry);

            report.setEditHistory(MAPPER.writeValueAsString(history));
        } catch (Exception e) {
            // Edit history is audit-only; don't fail the update if serialization breaks
        }
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

    private FixRequestResponse resolveActiveFixRequest(Long userId, Long reportId) {
        return fixRequestRepository.findFirstByReportReportIdAndState(reportId, FixRequestState.OPEN)
                .map(fr -> {
                    VoteType userVote = userId == null ? null
                            : fixRequestVoteRepository.findByUserIdAndFixRequestId(userId, fr.getId())
                                    .map(v -> v.getVoteType())
                                    .orElse(null);
                    return FixRequestResponse.fromEntity(fr, userVote);
                })
                .orElse(null);
    }

    private Map<Long, FixRequestResponse> resolveActiveFixRequests(Long userId, List<Report> reports) {
        if (reports.isEmpty()) return Collections.emptyMap();
        List<Long> reportIds = reports.stream().map(Report::getReportId).toList();
        List<Object[]> rows = fixRequestRepository.findOpenFixRequestIdsByReportIds(reportIds);
        if (rows.isEmpty()) return Collections.emptyMap();

        List<Long> fixRequestIds = rows.stream().map(row -> (Long) row[1]).toList();
        Map<Long, FixRequest> byId = fixRequestRepository.findAllById(fixRequestIds).stream()
                .collect(Collectors.toMap(FixRequest::getId, fr -> fr));

        Map<Long, VoteType> userVotesByFixId = userId == null ? Collections.emptyMap()
                : fixRequestVoteRepository.findVotesByUserIdAndFixRequestIds(userId, fixRequestIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (VoteType) row[1]));

        Map<Long, FixRequestResponse> result = new HashMap<>();
        for (Object[] row : rows) {
            Long reportId = (Long) row[0];
            Long fixId = (Long) row[1];
            FixRequest fr = byId.get(fixId);
            if (fr == null) continue;
            result.put(reportId, FixRequestResponse.fromEntity(fr, userVotesByFixId.get(fixId)));
        }
        return result;
    }

    private void broadcastAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { action.run(); }
        });
    }
}
