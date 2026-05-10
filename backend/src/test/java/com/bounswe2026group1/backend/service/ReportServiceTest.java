package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportObjectRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.*;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private MediaRepository mediaRepository;
    @Mock private ReportVerificationRepository verificationRepository;
    @Mock private FixRequestRepository fixRequestRepository;
    @Mock private FixRequestVoteRepository fixRequestVoteRepository;
    @Mock private PublicSseService publicSseService;
    @Mock private S3MediaService s3MediaService;
    @Mock private MeasurementValidator measurementValidator;
    @Mock private OverpassService overpassService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ReportService reportService;

    private RegisteredUser testUser;
    private Report testReport;
    private CreateReportRequest testRequest;

    @BeforeEach
    void setUp() {
        testUser = new RegisteredUser();
        testUser.setId(1L);
        testUser.setEmail("owner@test.com");

        testReport = new Report(testUser, GeoUtils.point4326(41.0, 29.0), "Broken ramp",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        testRequest = new CreateReportRequest();
        testRequest.setUserId(1L);
        testRequest.setLatitude(41.0);
        testRequest.setLongitude(29.0);
        testRequest.setDescription("Broken ramp");
        testRequest.setReportType(ReportType.OBSTACLE);
        testRequest.setEnvironment(ReportEnvironment.OUTDOOR);
        testRequest.setObjects(List.of(
                new ReportObjectRequest(ObjectType.RAMP, List.of(IssueType.TOO_STEEP), null)
        ));
    }

    @Test
    void getAll_returnsAllReports() {
        when(reportRepository.findAll()).thenReturn(List.of(testReport));

        List<ReportResponse> result = reportService.getAll(null);

        assertEquals(1, result.size());
        assertEquals("Broken ramp", result.get(0).getDescription());
        verify(reportRepository).findAll();
    }

    @Test
    void getAll_includesRejectedReports() {
        Report rejected = new Report(testUser, GeoUtils.point4326(41.1, 29.1), "Bogus", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReflectionTestUtils.setField(rejected, "status", ReportStatus.REJECTED);
        when(reportRepository.findAll()).thenReturn(List.of(testReport, rejected));

        List<ReportResponse> result = reportService.getAll(null);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getStatus() == ReportStatus.REJECTED));
    }

    @Test
    void getAll_withAuthenticatedUser_returnsUserVote() {
        testUser.setEmail("user@test.com");
        when(reportRepository.findAll()).thenReturn(List.of(testReport));
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(verificationRepository.findVotesByUserIdAndReportIds(eq(1L), any()))
                .thenReturn(List.<Object[]>of(new Object[]{testReport.getReportId(), VoteType.AGREE}));

        List<ReportResponse> result = reportService.getAll("user@test.com");

        assertEquals(1, result.size());
        assertEquals(VoteType.AGREE, result.get(0).getUserVote());
        verify(verificationRepository).findVotesByUserIdAndReportIds(eq(1L), any());
    }

    @Test
    void getById_existingId_returnsReport() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));

        Optional<ReportResponse> result = reportService.getById(1L, null);

        assertTrue(result.isPresent());
        assertEquals("Broken ramp", result.get().getDescription());
    }

    @Test
    void getById_nonExistingId_returnsEmpty() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<ReportResponse> result = reportService.getById(99L, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void create_validRequest_returnsCreatedReport() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        ReportResponse result = reportService.create(testRequest);

        assertNotNull(result);
        assertEquals("Broken ramp", result.getDescription());
        assertEquals(ReportStatus.PENDING, result.getStatus());
        assertEquals(ReportType.OBSTACLE, result.getReportType());
        verify(reportRepository, atLeastOnce()).save(any(Report.class));
        verify(publicSseService).broadcastReportCreated(testReport);
    }

    @Test
    void create_invalidUserId_throwsException() {
        when(registeredUserRepository.findById(99L)).thenReturn(Optional.empty());
        testRequest.setUserId(99L);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reportService.create(testRequest));

        assertTrue(exception.getMessage().contains("User not found"));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void create_invalidIssueForObjectType_throws400() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        // OUT_OF_SERVICE is only valid for ELEVATOR, not RAMP
        testRequest.setObjects(List.of(
                new ReportObjectRequest(ObjectType.RAMP, List.of(IssueType.OUT_OF_SERVICE), null)
        ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.create(testRequest));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void update_existingId_updatesFieldsAndBroadcasts() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        UpdateReportRequest updateRequest = new UpdateReportRequest();
        updateRequest.setDescription("Updated description");
        updateRequest.setLatitude(40.0);
        updateRequest.setLongitude(28.0);

        ReportResponse result = reportService.update(1L, updateRequest, "owner@test.com");

        assertNotNull(result);
        assertEquals("Updated description", testReport.getDescription());
        verify(reportRepository).save(testReport);
        verify(publicSseService).broadcastReportUpdated(testReport, "update");
    }

    @Test
    void update_reportNotFound_throws404() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateReportRequest updateRequest = new UpdateReportRequest();
        updateRequest.setDescription("desc");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.update(99L, updateRequest, "owner@test.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void update_withMediaIdsToRemove_deletesS3FilesAndRemovesFromList() {
        Media media = new Media();
        media.setFilePath("https://bucket.s3.amazonaws.com/file.jpg");
        ReflectionTestUtils.setField(media, "mediaId", 10L);
        testReport.getMediaList().add(media);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        UpdateReportRequest updateRequest = new UpdateReportRequest();
        updateRequest.setMediaIdsToRemove(List.of(10L));

        reportService.update(1L, updateRequest, "owner@test.com");

        verify(s3MediaService).deleteFile("https://bucket.s3.amazonaws.com/file.jpg");
        assertTrue(testReport.getMediaList().isEmpty());
    }

    @Test
    void delete_ownerDeletesOwnReport_deletesAndBroadcasts() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(testUser));

        reportService.delete(1L, "owner@test.com");

        verify(reportRepository).delete(testReport);
        verify(publicSseService).broadcastReportDeleted(1L);
    }

    @Test
    void delete_reportNotFound_throws404() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.delete(99L, "owner@test.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(reportRepository, never()).delete(any(Report.class));
    }

    @Test
    void delete_notOwner_throws403() {
        RegisteredUser otherUser = new RegisteredUser();
        otherUser.setId(2L);
        otherUser.setEmail("other@test.com");

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.delete(1L, "other@test.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(reportRepository, never()).delete(any(Report.class));
    }

    @Test
    void delete_adminDeletesOtherUsersReport_deletesAndBroadcasts() {
        RegisteredUser admin = new RegisteredUser();
        admin.setId(2L);
        admin.setEmail("admin@test.com");
        admin.setRole(UserRole.ADMIN);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        reportService.delete(1L, "admin@test.com");

        verify(reportRepository).delete(testReport);
        verify(publicSseService).broadcastReportDeleted(1L);
    }

    @Test
    void delete_withMedia_callsS3DeleteForEachFile() {
        Media media = new Media();
        media.setFilePath("https://bucket.s3.amazonaws.com/file.jpg");
        testReport.getMediaList().add(media);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(testUser));

        reportService.delete(1L, "owner@test.com");

        verify(s3MediaService).deleteFile("https://bucket.s3.amazonaws.com/file.jpg");
    }

    @Test
    void delete_s3DeleteFails_stillRemovesReport() {
        Media media = new Media();
        media.setFilePath("https://not-our-bucket.example.com/orphan.jpg");
        testReport.getMediaList().add(media);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(registeredUserRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(testUser));
        doThrow(new IllegalArgumentException("URL does not belong to this bucket"))
                .when(s3MediaService).deleteFile(any());

        reportService.delete(1L, "owner@test.com");

        verify(reportRepository).delete(testReport);
    }

    @Test
    void verifyReport_reachesThreshold_changesStatusToVerified() {
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(5, testReport.getAgrees());
        assertEquals(ReportStatus.VERIFIED, testReport.getStatus());
        verify(reportRepository).save(testReport);
        verify(publicSseService).broadcastReportUpdated(testReport, "verify");
    }

    @Test
    void verifyReport_belowThreshold_statusRemainsPending() {
        ReflectionTestUtils.setField(testReport, "agrees", 1);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(2, testReport.getAgrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(reportRepository).save(testReport);
    }

    @Test
    void verifyReport_toggleOff_removesAgree() {
        ReflectionTestUtils.setField(testReport, "agrees", 1);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        testUser.setEmail("user@test.com");
        ReportVerification existing = new ReportVerification(testUser, testReport, VoteType.AGREE);

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(0, testReport.getAgrees());
        verify(verificationRepository).delete(existing);
    }

    @Test
    void unverifyReport_incrementsDisagrees() {
        ReflectionTestUtils.setField(testReport, "disagrees", 0);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(1, testReport.getDisagrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(reportRepository).save(testReport);
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
    }

    @Test
    void addMediaToReport_broadcastsMediaAdded() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.addMediaToReport(1L, "https://cdn.example/media.jpg");

        verify(mediaRepository).save(any(Media.class));
        verify(publicSseService).broadcastMediaAdded(eq(testReport), any(Media.class));
    }

    @Test
    void unverifyReport_verifiedDropsBelowRatio_revertsToPending() {
        // Pre-state: VERIFIED report with agrees=5, disagrees=4. New disagree -> 5/10 = 0.5 < 0.6
        // and disagrees=5 satisfies the count threshold (req 1.2.3.8) -> revert to PENDING.
        ReflectionTestUtils.setField(testReport, "agrees", 5);
        ReflectionTestUtils.setField(testReport, "disagrees", 4);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.VERIFIED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
    }

    @Test
    void verifyReport_nonExistingId_throwsNoSuchElementException() {
        testUser.setEmail("user@test.com");
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> reportService.verifyReport(99L, "user@test.com"));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void unverifyReport_nonExistingId_throwsNoSuchElementException() {
        testUser.setEmail("user@test.com");
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> reportService.unverifyReport(99L, "user@test.com"));
        verify(reportRepository, never()).save(any());
    }

    // ---------------- Fix-request hydration on responses ----------------

    @Test
    void getById_withActiveFixRequest_includesFixRequestInResponse() {
        ReflectionTestUtils.setField(testReport, "reportId", 1L);
        FixRequest fixRequest = new FixRequest(testReport, testUser, "Looks fixed");
        ReflectionTestUtils.setField(fixRequest, "id", 7L);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(fixRequestRepository.findFirstByReportReportIdAndState(1L, FixRequestState.OPEN))
                .thenReturn(Optional.of(fixRequest));

        Optional<ReportResponse> result = reportService.getById(1L, null);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getActiveFixRequest());
        assertEquals(7L, result.get().getActiveFixRequest().getId());
        assertEquals(FixRequestState.OPEN, result.get().getActiveFixRequest().getState());
    }

    @Test
    void getById_withoutActiveFixRequest_activeFixRequestIsNull() {
        ReflectionTestUtils.setField(testReport, "reportId", 1L);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(fixRequestRepository.findFirstByReportReportIdAndState(1L, FixRequestState.OPEN))
                .thenReturn(Optional.empty());

        Optional<ReportResponse> result = reportService.getById(1L, null);

        assertTrue(result.isPresent());
        assertNull(result.get().getActiveFixRequest());
    }

    @Test
    void verifyReport_responseIncludesActiveFixRequestWhenPresent() {
        // Regression: every mutation that returns a report must hydrate activeFixRequest,
        // otherwise the frontend wipes the live fix card from its cache after a vote on
        // the parent report.
        ReflectionTestUtils.setField(testReport, "reportId", 1L);
        FixRequest fix = new FixRequest(testReport, testUser, "Looks fixed");
        ReflectionTestUtils.setField(fix, "id", 7L);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(fixRequestRepository.findFirstByReportReportIdAndState(1L, FixRequestState.OPEN))
                .thenReturn(Optional.of(fix));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        ReportResponse response = reportService.verifyReport(1L, "user@test.com");

        assertNotNull(response.getActiveFixRequest());
        assertEquals(7L, response.getActiveFixRequest().getId());
    }

    // ---------------- Block votes on FIXED reports ----------------

    @Test
    void verifyReport_onFixedReport_throwsConflict() {
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.FIXED);
        testUser.setEmail("user@test.com");
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.verifyReport(1L, "user@test.com"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void unverifyReport_onFixedReport_throwsConflict() {
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.FIXED);
        testUser.setEmail("user@test.com");
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.unverifyReport(1L, "user@test.com"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    // ---------------- Ratio-based status transitions (req 1.2.3.7-9) ----------------

    @Test
    void verifyReport_meetsCountButBelowRatio_staysPending() {
        // agrees=4 + 1 new = 5 vs disagrees=4 -> 5/9 = 0.555 < 0.60 -> stays PENDING
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(testReport, "disagrees", 4);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
    }

    @Test
    void verifyReport_meetsCountAndRatio_becomesVerified() {
        // agrees=4 + 1 new = 5 vs disagrees=2 -> 5/7 = 0.714 >= 0.60 -> VERIFIED
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(testReport, "disagrees", 2);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.VERIFIED, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "verify");
    }

    @Test
    void unverifyReport_disagreesReachThresholdAndRatio_becomesRejected() {
        // disagrees=4 + 1 new = 5 vs agrees=2 -> 5/7 = 0.714 >= 0.60 -> REJECTED
        ReflectionTestUtils.setField(testReport, "agrees", 2);
        ReflectionTestUtils.setField(testReport, "disagrees", 4);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.REJECTED, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "reject");
    }

    @Test
    void unverifyReport_disagreesAboveCountButBelowRatio_staysPending() {
        // disagrees=4 + 1 new = 5 vs agrees=4 -> 5/9 = 0.555 < 0.60 -> stays PENDING
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(testReport, "disagrees", 4);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
    }

    @Test
    void verifyReport_rejectedRecoversToPending() {
        // Pre REJECTED, disagrees=5, agrees=4 + 1 new = 5 -> 0.5/0.5 -> neither flip cond holds,
        // REJECTED-revert branch: agrees>=5 AND disagreeRatio<0.60 -> PENDING
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(testReport, "disagrees", 5);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.REJECTED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
    }

    @Test
    void verifyReport_retractAgreeFromVerifiedDropsBelowThreshold_revertsToPending() {
        // Pre VERIFIED, agrees=5, disagrees=0. User retracts their agree -> agrees=4.
        // Verification criteria no longer hold (4 < 5) -> revert to PENDING.
        ReflectionTestUtils.setField(testReport, "agrees", 5);
        ReflectionTestUtils.setField(testReport, "disagrees", 0);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.VERIFIED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");
        ReportVerification existing = new ReportVerification(testUser, testReport, VoteType.AGREE);

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(4, testReport.getAgrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
        verify(notificationService).notifyStatusChange(testReport, testUser.getId());
    }

    @Test
    void unverifyReport_retractDisagreeFromRejectedDropsBelowThreshold_revertsToPending() {
        // Pre REJECTED, disagrees=5, agrees=0. User retracts their disagree -> disagrees=4.
        // Rejection criteria no longer hold (4 < 5) -> revert to PENDING.
        ReflectionTestUtils.setField(testReport, "agrees", 0);
        ReflectionTestUtils.setField(testReport, "disagrees", 5);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.REJECTED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");
        ReportVerification existing = new ReportVerification(testUser, testReport, VoteType.DISAGREE);

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(4, testReport.getDisagrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "pending");
        verify(notificationService).notifyStatusChange(testReport, testUser.getId());
    }

    @Test
    void verifyReport_rejectedRecoversDirectlyToVerified() {
        // Pre REJECTED, disagrees=5, agrees=7 + 1 new = 8 -> 8/13 = 0.615 >= 0.60 -> VERIFIED
        ReflectionTestUtils.setField(testReport, "agrees", 7);
        ReflectionTestUtils.setField(testReport, "disagrees", 5);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.REJECTED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        ReflectionTestUtils.setField(reportService, "verificationRatio", 0.60);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.verifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.VERIFIED, testReport.getStatus());
        verify(publicSseService).broadcastReportUpdated(testReport, "verify");
    }

    // ---------------- REJECTED visibility ----------------
    // Backend exposes REJECTED reports to all callers; frontend decides
    // how to render them (e.g., hide pin from public map). See req 1.2.3.9.

    @Test
    void getById_rejected_returnsToAnyone() {
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.REJECTED);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));

        Optional<ReportResponse> result = reportService.getById(1L, null);

        assertTrue(result.isPresent());
        assertEquals(ReportStatus.REJECTED, result.get().getStatus());
    }

    @Test
    void getByUserId_includesRejected() {
        Report rejected = new Report(testUser, GeoUtils.point4326(41.1, 29.1), "Bogus", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReflectionTestUtils.setField(rejected, "status", ReportStatus.REJECTED);

        when(reportRepository.findByCreatedById(1L)).thenReturn(List.of(testReport, rejected));

        List<ReportResponse> result = reportService.getByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getStatus() == ReportStatus.REJECTED));
    }

    // ---------------- Feed (GET /api/reports/feed) ----------------

    @Test
    void feed_withoutCoordinates_callsFindFeedRecent() {
        ReflectionTestUtils.setField(testReport, "reportId", 10L);
        Page<Report> repoPage = new PageImpl<>(List.of(testReport));
        when(reportRepository.findFeedRecent(isNull(), isNull(), any(Pageable.class))).thenReturn(repoPage);
        when(fixRequestRepository.findOpenFixRequestIdsByReportIds(anyList())).thenReturn(List.of());

        Page<ReportResponse> result = reportService.feed(
                null, null, null, null, null,
                PageRequest.of(0, 20),
                null);

        assertEquals(1, result.getContent().size());
        assertEquals("Broken ramp", result.getContent().get(0).getDescription());
        verify(reportRepository).findFeedRecent(null, null, PageRequest.of(0, 20));
        verify(reportRepository, never()).findFeedWithinRadius(
                any(), any(), anyDouble(), anyDouble(), anyDouble(), any(Pageable.class));
    }

    @Test
    void feed_withCoordinates_callsFindFeedWithinRadius_withDefaultRadiusKmWhenNull() {
        ReflectionTestUtils.setField(testReport, "reportId", 11L);
        Page<Report> repoPage = new PageImpl<>(List.of(testReport));
        Pageable expectedPaging = PageRequest.of(0, 20, Sort.unsorted());
        when(reportRepository.findFeedWithinRadius(
                isNull(),
                isNull(),
                eq(41.0),
                eq(29.0),
                eq(1.0),
                eq(expectedPaging))).thenReturn(repoPage);
        when(fixRequestRepository.findOpenFixRequestIdsByReportIds(anyList())).thenReturn(List.of());

        Page<ReportResponse> result = reportService.feed(
                null, null, 41.0, 29.0, null,
                PageRequest.of(0, 20),
                null);

        assertEquals(1, result.getTotalElements());
        verify(reportRepository).findFeedWithinRadius(
                null, null, 41.0, 29.0, 1.0, PageRequest.of(0, 20, Sort.unsorted()));
        verify(reportRepository, never()).findFeedRecent(any(), any(), any(Pageable.class));
    }

    @Test
    void feed_withCoordinatesAndFilters_passesReportTypeEnvironmentAndRadiusToProximityRepository() {
        ReflectionTestUtils.setField(testReport, "reportId", 12L);
        Page<Report> repoPage = new PageImpl<>(List.of(testReport));
        Pageable expectedPaging = PageRequest.of(0, 10, Sort.unsorted());
        when(reportRepository.findFeedWithinRadius(
                eq(ReportType.OBSTACLE),
                eq(ReportEnvironment.INDOOR),
                eq(41.5),
                eq(29.5),
                eq(3.0),
                eq(expectedPaging))).thenReturn(repoPage);
        when(fixRequestRepository.findOpenFixRequestIdsByReportIds(anyList())).thenReturn(List.of());

        reportService.feed(
                ReportType.OBSTACLE,
                ReportEnvironment.INDOOR,
                41.5,
                29.5,
                3.0,
                PageRequest.of(0, 10),
                null);

        verify(reportRepository).findFeedWithinRadius(
                ReportType.OBSTACLE,
                ReportEnvironment.INDOOR,
                41.5,
                29.5,
                3.0,
                PageRequest.of(0, 10, Sort.unsorted()));
    }

    @Test
    void feed_latitudeWithoutLongitude_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.feed(null, null, 41.0, null, null, PageRequest.of(0, 20), null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

}
