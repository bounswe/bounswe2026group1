package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.MeasurementWarning;
import com.bounswe2026group1.backend.dto.ReportFeedQueryRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bounswe2026group1.backend.repository.ReportSpecifications.feed;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double COORD_EPSILON = 1e-6;

    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final ReportCategoryRepository categoryRepository;
    private final MediaRepository mediaRepository;
    private final ReportVerificationRepository verificationRepository;
    private final PublicSseService publicSseService;
    private final S3MediaService s3MediaService;
    private final MeasurementValidator measurementValidator;
    private final OverpassService overpassService;

    @Value("${app.report.verification.threshold:5}")
    private int verificationThreshold;

    public List<ReportResponse> getAll(String email) {
        Long userId = resolveUserId(email);
        List<Report> reports = reportRepository.findAll();
        Map<Long, VoteType> votesByReportId = resolveUserVotes(userId, reports);
        return reports.stream()
                .map(r -> {
                    List<MeasurementWarning> warnings = computeWarnings(r);
                    return ReportResponse.fromEntity(r, votesByReportId.get(r.getReportId()), warnings);
                })
                .toList();
    }

    public Optional<ReportResponse> getById(Long id, String email) {
        Long userId = resolveUserId(email);
        return reportRepository.findById(id)
                .map(r -> ReportResponse.fromEntity(r, resolveUserVote(userId, r.getReportId()), computeWarnings(r)));
    }

    public List<ReportResponse> getByUserId(Long userId) {
        return reportRepository.findByCreatedById(userId).stream()
                .map(r -> ReportResponse.fromEntity(r, null, computeWarnings(r)))
                .toList();
    }

    /**
     * Paginated reports feed with optional filters. Category filter uses the subtree (BFS) of {@link ReportFeedQueryRequest#getCategoryId()}.
     * Type filter uses resolved {@link ReportType} per category (walk to root), computed in memory for correctness with inherited types.
     */
    @Transactional(readOnly = true)
    public Page<ReportResponse> getFeed(ReportFeedQueryRequest query, Pageable pageable, String email) {
        Long viewerId = resolveUserId(email);
        List<ReportCategory> allCategories = categoryRepository.findAll();

        Map<Long, Long> parentByCategoryId = new HashMap<>();
        Map<Long, String> nameByCategoryId = new HashMap<>();
        Map<Long, ReportCategory> categoryById = new HashMap<>();
        Map<Long, List<Long>> childrenByParentId = new HashMap<>();

        for (ReportCategory c : allCategories) {
            Long id = c.getId();
            categoryById.put(id, c);
            nameByCategoryId.put(id, c.getName());
            Long parentId = c.getParent() != null ? c.getParent().getId() : null;
            parentByCategoryId.put(id, parentId);
            if (c.getParent() != null) {
                childrenByParentId
                        .computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>())
                        .add(id);
            }
        }

        Function<Long, List<String>> pathResolver =
                leafId -> buildCategoryPath(leafId, parentByCategoryId, nameByCategoryId);

        Collection<Long> subtreeFilter = null;
        if (query.getCategoryId() != null) {
            if (!categoryById.containsKey(query.getCategoryId())) {
                return Page.empty(pageable);
            }
            subtreeFilter = collectSubtreeCategoryIds(query.getCategoryId(), childrenByParentId);
        }

        Collection<Long> typeCategoryIds = null;
        if (query.getType() != null) {
            Set<Long> ids = new HashSet<>();
            for (ReportCategory c : allCategories) {
                if (query.getType() == resolveCategoryType(c.getId(), parentByCategoryId, categoryById)) {
                    ids.add(c.getId());
                }
            }
            if (ids.isEmpty()) {
                return Page.empty(pageable);
            }
            typeCategoryIds = ids;
        }

        Specification<Report> spec = feed(subtreeFilter, typeCategoryIds, query.getEnvironment(), query.getUserId());
        Page<Report> reportPage = reportRepository.findAll(spec, pageable);
        Map<Long, VoteType> votesByReportId = resolveUserVotes(viewerId, reportPage.getContent());

        return reportPage.map(r ->
                ReportResponse.fromEntity(
                        r,
                        votesByReportId.get(r.getReportId()),
                        computeWarnings(r),
                        pathResolver));
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request) {
        RegisteredUser user = registeredUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + request.getUserId()));

        ReportCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Category not found with id: " + request.getCategoryId()));

        if (categoryRepository.existsByParentId(category.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only leaf categories can be assigned to a report");
        }

        Location location = new Location(request.getLatitude(), request.getLongitude());
        Report report = new Report(user, location, request.getDescription(), category, request.getEnvironment());

        if (category.isRoutingRelevant() && category.getSnapType() != null) {
            try {
                Location[] endpoints = switch (category.getSnapType()) {
                    case STAIR -> overpassService.snapToNearestStair(location);
                };
                report.setEntryPoint(endpoints[0]);
                report.setExitPoint(endpoints[1]);
            } catch (RoutingException e) {
                throw e;
            } catch (Exception e) {
                throw new RoutingException(HttpStatus.BAD_REQUEST,
                        "Could not verify stair location. Please try again later.");
            }
        }

        List<MeasurementWarning> warnings = List.of();
        if (request.getMeasurements() != null) {
            warnings = measurementValidator.validate(request.getMeasurements(), category.getMeasurementSchema());
            report.setMeasurements(request.getMeasurements());
        }

        Report saved = reportRepository.save(report);
        broadcastAfterCommit(() -> publicSseService.broadcastReportCreated(saved));

        return ReportResponse.fromEntity(saved, null, warnings);
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

        if (request.getCategoryId() != null && !request.getCategoryId().equals(report.getCategory().getId())) {
            ReportCategory newCategory = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Category not found with id: " + request.getCategoryId()));
            if (categoryRepository.existsByParentId(newCategory.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only leaf categories can be assigned to a report");
            }
            diff.put("categoryId", new Object[]{report.getCategory().getId(), request.getCategoryId()});
            report.setCategory(newCategory);
        }

        if (request.getEnvironment() != null && request.getEnvironment() != report.getEnvironment()) {
            diff.put("environment", new Object[]{report.getEnvironment(), request.getEnvironment()});
            report.setEnvironment(request.getEnvironment());
        }

        if (request.getLatitude() != null && report.getLocation() != null
                && Math.abs(request.getLatitude() - report.getLocation().getLatitude()) > COORD_EPSILON) {
            diff.put("latitude", new Object[]{report.getLocation().getLatitude(), request.getLatitude()});
            report.getLocation().setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null && report.getLocation() != null
                && Math.abs(request.getLongitude() - report.getLocation().getLongitude()) > COORD_EPSILON) {
            diff.put("longitude", new Object[]{report.getLocation().getLongitude(), request.getLongitude()});
            report.getLocation().setLongitude(request.getLongitude());
        }

        List<MeasurementWarning> warnings = List.of();
        if (request.getMeasurements() != null) {
            warnings = measurementValidator.validate(request.getMeasurements(), report.getCategory().getMeasurementSchema());
            diff.put("measurements", new Object[]{report.getMeasurements(), request.getMeasurements()});
            report.setMeasurements(request.getMeasurements());
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

        return ReportResponse.fromEntity(saved, resolveUserVote(editor.getId(), saved.getReportId()), warnings);
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
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found with id: " + reportId));
        Media media = new Media();
        media.setReport(report);
        media.setFilePath(mediaUrl);
        Media savedMedia = mediaRepository.save(media);
        broadcastAfterCommit(() -> publicSseService.broadcastMediaAdded(report, savedMedia));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<MeasurementWarning> computeWarnings(Report report) {
        if (report.getMeasurements() == null || report.getCategory() == null) return List.of();
        return measurementValidator.validate(report.getMeasurements(), report.getCategory().getMeasurementSchema());
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
        if (email == null || email.isBlank()) return null;
        if ("anonymousUser".equalsIgnoreCase(email)) return null;
        return registeredUserRepository.findByEmail(email)
                .map(RegisteredUser::getId)
                .orElse(null);
    }

    private static Set<Long> collectSubtreeCategoryIds(Long rootId, Map<Long, List<Long>> childrenByParentId) {
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            for (Long childId : childrenByParentId.getOrDefault(id, List.of())) {
                queue.add(childId);
            }
        }
        return visited;
    }

    private static ReportType resolveCategoryType(Long categoryId, Map<Long, Long> parentByCategoryId,
                                                   Map<Long, ReportCategory> categoryById) {
        Long id = categoryId;
        for (int i = 0; i < 64 && id != null; i++) {
            ReportCategory c = categoryById.get(id);
            if (c == null) {
                return null;
            }
            if (c.getType() != null) {
                return c.getType();
            }
            id = parentByCategoryId.get(id);
        }
        return null;
    }

    private static List<String> buildCategoryPath(Long leafCategoryId,
                                                  Map<Long, Long> parentByCategoryId,
                                                  Map<Long, String> nameByCategoryId) {
        if (leafCategoryId == null) {
            return List.of();
        }
        LinkedList<String> segments = new LinkedList<>();
        Long id = leafCategoryId;
        for (int i = 0; i < 64 && id != null; i++) {
            String name = nameByCategoryId.get(id);
            if (name != null) {
                segments.addFirst(name);
            }
            id = parentByCategoryId.get(id);
        }
        return new ArrayList<>(segments);
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
