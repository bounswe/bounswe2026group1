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
import org.springframework.data.domain.Sort;
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
    private final UserBadgeRepository userBadgeRepository;
    private final PublicSseService publicSseService;
    private final NotificationService notificationService;
    private final GamificationService gamificationService;
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
        Map<Long, Badge> topBadgesByAuthor = resolveAuthorTopBadges(reports);
        return reports.stream()
                .map(r -> withAuthorTopBadge(
                        ReportResponse.fromEntity(
                                r,
                                votesByReportId.get(r.getReportId()),
                                buildObjectResponses(r),
                                activeFixByReportId.get(r.getReportId())),
                        r, topBadgesByAuthor))
                .toList();
    }

    public Optional<ReportResponse> getById(Long id, String email) {
        Long userId = resolveUserId(email);
        return reportRepository.findById(id)
                .map(r -> {
                    ReportResponse resp = ReportResponse.fromEntity(
                            r,
                            resolveUserVote(userId, r.getReportId()),
                            buildObjectResponses(r),
                            resolveActiveFixRequest(userId, r.getReportId()));
                    resp.setAuthorTopBadge(resolveAuthorTopBadge(r.getCreatedBy().getId()));
                    return resp;
                });
    }

    public List<ReportResponse> getByUserId(Long userId) {
        List<Report> reports = reportRepository.findByCreatedById(userId);
        Map<Long, Badge> topBadgesByAuthor = resolveAuthorTopBadges(reports);
        return reports.stream()
                .map(r -> withAuthorTopBadge(
                        ReportResponse.fromEntity(
                                r,
                                resolveUserVote(userId, r.getReportId()),
                                buildObjectResponses(r),
                                resolveActiveFixRequest(userId, r.getReportId())),
                        r, topBadgesByAuthor))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> feed(
            ReportType reportType,
            ReportEnvironment environment,
            Double latitude,
            Double longitude,
            Double radiusInKm,
            Pageable pageable,
            String email) {

        if (latitude != null ^ longitude != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude and longitude must both be provided for proximity feed");
        }

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(100, Math.max(1, pageable.getPageSize()));
        Pageable paged = latitude != null
                ? PageRequest.of(pageNumber, pageSize, Sort.unsorted())
                : PageRequest.of(pageNumber, pageSize);

        Long userId = resolveUserId(email);

        Page<Report> page = latitude != null
                ? reportRepository.findFeedWithinRadius(
                        reportType,
                        environment,
                        latitude,
                        longitude,
                        radiusInKm != null ? radiusInKm : 1.0,
                        paged)
                : reportRepository.findFeedRecent(reportType, environment, paged);

        Map<Long, VoteType> votesByReportId = resolveUserVotes(userId, page.getContent());
        Map<Long, FixRequestResponse> activeFixByReportId = resolveActiveFixRequests(userId, page.getContent());
        Map<Long, Badge> topBadgesByAuthor = resolveAuthorTopBadges(page.getContent());
        List<ReportResponse> responses = page.getContent().stream()
                .map(r -> withAuthorTopBadge(
                        ReportResponse.fromEntity(
                                r,
                                votesByReportId.get(r.getReportId()),
                                buildObjectResponses(r),
                                activeFixByReportId.get(r.getReportId())),
                        r, topBadgesByAuthor))
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

        if (request.getGeometry() != null) {
            try {
                request.getGeometry().validate();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        double lat = request.effectiveLatitude();
        double lon = request.effectiveLongitude();
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Report location is required — supply `geometry` (GeoJSON Point) or `latitude`/`longitude`");
        }

        Location reportedPoint = new Location(lat, lon);
        Point reportLocation = GeoUtils.point4326(lat, lon);
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

        gamificationService.awardOnReportSubmit(user, saved.getReportId());

        ReportResponse response = ReportResponse.fromEntity(saved, null, buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
        response.setAuthorTopBadge(resolveAuthorTopBadge(user.getId()));
        return response;
    }

    @Transactional
    public ReportResponse update(Long id, UpdateReportRequest request, String editorEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found with id: " + id));

        RegisteredUser editor = registeredUserRepository.findByEmail(editorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        boolean isOwner = report.getCreatedBy().getId().equals(editor.getId());
        boolean isAdmin = editor.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }

        Map<String, Object[]> diff = new LinkedHashMap<>();

        if (request.getDescription() != null && !request.getDescription().equals(report.getDescription())) {
            diff.put("description", new Object[]{report.getDescription(), request.getDescription()});
            report.setDescription(request.getDescription());
        }

        if (request.getEnvironment() != null && request.getEnvironment() != report.getEnvironment()) {
            diff.put("environment", new Object[]{report.getEnvironment(), request.getEnvironment()});
            report.setEnvironment(request.getEnvironment());
        }

        if (request.getGeometry() != null) {
            try {
                request.getGeometry().validate();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        Double newLat = request.effectiveLatitude();
        Double newLon = request.effectiveLongitude();
        if (newLat != null && Math.abs(newLat - report.getLocation().getY()) > 1e-9) {
            diff.put("latitude", new Object[]{report.getLocation().getY(), newLat});
            report.setLocation(GeoUtils.point4326(newLat, report.getLocation().getX()));
        }
        if (newLon != null && Math.abs(newLon - report.getLocation().getX()) > 1e-9) {
            diff.put("longitude", new Object[]{report.getLocation().getX(), newLon});
            report.setLocation(GeoUtils.point4326(report.getLocation().getY(), newLon));
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

        ReportResponse response = ReportResponse.fromEntity(saved,
                resolveUserVote(editor.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(editor.getId(), saved.getReportId()));
        response.setAuthorTopBadge(resolveAuthorTopBadge(saved.getCreatedBy().getId()));
        return response;
    }

    @Transactional
    public void delete(Long id, String email) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found with id: " + id));

        RegisteredUser requester = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        boolean isOwner = report.getCreatedBy().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }

        // Best-effort S3 cleanup: a failed delete (e.g. orphan/non-bucket URL) must not block
        // the DB row removal — same pattern as RegisteredUserController.deleteAvatar.
        report.getMediaList().forEach(m -> deleteS3Quietly(m.getFilePath(), id));
        report.getFixRequests().forEach(fr ->
                fr.getMediaList().forEach(frm -> deleteS3Quietly(frm.getFilePath(), id)));
        reportRepository.delete(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportDeleted(id));
    }

    private void deleteS3Quietly(String url, Long reportId) {
        if (url == null || url.isBlank()) return;
        try {
            s3MediaService.deleteFile(url);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object for report {} ({}): {}", reportId, url, e.getMessage());
        }
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

        // Three branches: same-direction toggle = withdraw (delete row),
        // opposite-direction = modify (no extra points), absent = fresh cast.
        boolean fresh = false;
        boolean withdrawn = false;
        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.AGREE) {
                report.decrementAgrees();
                verificationRepository.delete(existing.get());
                withdrawn = true;
            } else {
                report.decrementDisagrees();
                report.incrementAgrees();
                existing.get().setVoteType(VoteType.AGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            report.incrementAgrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.AGREE));
            fresh = true;
        }

        ReportStatus previousStatus = report.getStatus();
        ReportStatus newStatus = evaluateStatus(report);

        Report saved = reportRepository.save(report);
        if (fresh) {
            gamificationService.awardOnVoteCast(user, saved.getReportId());
        } else if (withdrawn) {
            gamificationService.deductOnVoteWithdraw(user, saved.getReportId());
        }
        String op = switch (newStatus) { case VERIFIED -> "verify"; case REJECTED -> "reject"; default -> "pending"; };
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, op));
        if (newStatus != previousStatus) {
            notificationService.notifyStatusChange(saved, user.getId());
            gamificationService.onReportStatusTransition(saved, previousStatus, newStatus);
        }
        ReportResponse response = ReportResponse.fromEntity(saved,
                resolveUserVote(user.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
        response.setAuthorTopBadge(resolveAuthorTopBadge(saved.getCreatedBy().getId()));
        return response;
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

        // Three branches: same-direction toggle = withdraw (delete row),
        // opposite-direction = modify (no extra points), absent = fresh cast.
        boolean fresh = false;
        boolean withdrawn = false;
        if (existing.isPresent()) {
            if (existing.get().getVoteType() == VoteType.DISAGREE) {
                report.decrementDisagrees();
                verificationRepository.delete(existing.get());
                withdrawn = true;
            } else {
                report.decrementAgrees();
                report.incrementDisagrees();
                existing.get().setVoteType(VoteType.DISAGREE);
                verificationRepository.save(existing.get());
            }
        } else {
            report.incrementDisagrees();
            verificationRepository.save(new ReportVerification(user, report, VoteType.DISAGREE));
            fresh = true;
        }

        ReportStatus previousStatus = report.getStatus();
        ReportStatus newStatus = evaluateStatus(report);

        Report saved = reportRepository.save(report);
        if (fresh) {
            gamificationService.awardOnVoteCast(user, saved.getReportId());
        } else if (withdrawn) {
            gamificationService.deductOnVoteWithdraw(user, saved.getReportId());
        }
        String op = switch (newStatus) { case VERIFIED -> "verify"; case REJECTED -> "reject"; default -> "pending"; };
        broadcastAfterCommit(() -> publicSseService.broadcastReportUpdated(saved, op));
        if (newStatus != previousStatus) {
            notificationService.notifyStatusChange(saved, user.getId());
            gamificationService.onReportStatusTransition(saved, previousStatus, newStatus);
        }
        ReportResponse response = ReportResponse.fromEntity(saved,
                resolveUserVote(user.getId(), saved.getReportId()),
                buildObjectResponses(saved),
                resolveActiveFixRequest(user.getId(), saved.getReportId()));
        response.setAuthorTopBadge(resolveAuthorTopBadge(saved.getCreatedBy().getId()));
        return response;
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
        } else if (current == ReportStatus.VERIFIED || current == ReportStatus.REJECTED) {
            // Verification/rejection criteria no longer hold (count or ratio fell below
            // threshold, e.g. via vote retraction). Revert to PENDING symmetrically.
            target = ReportStatus.PENDING;
        }

        if (target != current) {
            report.setStatus(target);
        }
        return target;
    }

    public Media addMediaToReport(Long reportId, String mediaUrl, String email) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));

        RegisteredUser requester = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        boolean isOwner = report.getCreatedBy().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }
        Media media = new Media();
        media.setReport(report);
        media.setFilePath(mediaUrl);
        Media savedMedia = mediaRepository.save(media);
        broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
        return savedMedia;
    }

    @Transactional
    public List<Media> addMediaToReportBatch(Long reportId, List<String> mediaUrls, String email) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));

        RegisteredUser requester = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        boolean isOwner = report.getCreatedBy().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the owner of this report");
        }
        List<Media> savedMediaList = new ArrayList<>();
        for (String mediaUrl : mediaUrls) {
            Media media = new Media();
            media.setReport(report);
            media.setFilePath(mediaUrl);
            Media savedMedia = mediaRepository.save(media);
            savedMediaList.add(savedMedia);
            broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
        }
        return savedMediaList;
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

    /** Inline setter used inside list-stream callsites — picks the author's
     *  top badge from the prefetched map and attaches it to the response.
     *  Returns the same response so it can chain inside a {@code map(...)}. */
    private static ReportResponse withAuthorTopBadge(ReportResponse response,
                                                     Report report,
                                                     Map<Long, Badge> topBadgesByAuthor) {
        if (report.getCreatedBy() != null) {
            response.setAuthorTopBadge(topBadgesByAuthor.get(report.getCreatedBy().getId()));
        }
        return response;
    }

    /** Single-author lookup — used after create/update/vote where we already
     *  have the author entity in scope. Picks the highest-tier badge to surface
     *  next to the author chip on the report panel. */
    private Badge resolveAuthorTopBadge(Long authorId) {
        if (authorId == null) return null;
        List<Badge> badges = userBadgeRepository.findByUserId(authorId).stream()
                .map(UserBadge::getBadge)
                .toList();
        return Badge.pickHighestTier(badges);
    }

    /** Batch variant for list/feed paths — single query for all distinct
     *  author ids, then a tier-pick per author. Avoids N+1 on /api/reports
     *  and the feed paginator. */
    private Map<Long, Badge> resolveAuthorTopBadges(Collection<Report> reports) {
        if (reports.isEmpty()) return Map.of();
        Set<Long> authorIds = reports.stream()
                .map(r -> r.getCreatedBy() != null ? r.getCreatedBy().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) return Map.of();

        Map<Long, List<Badge>> byUser = new HashMap<>();
        for (Object[] row : userBadgeRepository.findBadgesByUserIds(authorIds)) {
            byUser.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Badge) row[1]);
        }
        Map<Long, Badge> result = new HashMap<>();
        byUser.forEach((id, list) -> result.put(id, Badge.pickHighestTier(list)));
        return result;
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
