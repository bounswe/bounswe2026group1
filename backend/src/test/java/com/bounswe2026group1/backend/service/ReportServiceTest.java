package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private ReportCategoryRepository categoryRepository;
    @Mock private MediaRepository mediaRepository;
    @Mock private ReportVerificationRepository verificationRepository;
    @Mock private PublicSseService publicSseService;
    @Mock private S3MediaService s3MediaService;
    @Mock private MeasurementValidator measurementValidator;
    @Mock private OverpassService overpassService;

    @InjectMocks
    private ReportService reportService;

    private RegisteredUser testUser;
    private ReportCategory testCategory;
    private Report testReport;
    private CreateReportRequest testRequest;

    @BeforeEach
    void setUp() {
        testUser = new RegisteredUser();
        testUser.setId(1L);
        testUser.setEmail("owner@test.com");

        testCategory = new ReportCategory();
        ReflectionTestUtils.setField(testCategory, "id", 6L);
        testCategory.setName("Too Steep");
        testCategory.setType(ReportType.OBSTACLE);

        testReport = new Report(testUser, new Location(41.0, 29.0), "Broken ramp", testCategory, ReportEnvironment.OUTDOOR);

        testRequest = new CreateReportRequest();
        testRequest.setUserId(1L);
        testRequest.setLatitude(41.0);
        testRequest.setLongitude(29.0);
        testRequest.setDescription("Broken ramp");
        testRequest.setCategoryId(6L);
        testRequest.setEnvironment(ReportEnvironment.OUTDOOR);
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
    void getAll_withAuthenticatedUser_returnsUserVote() {
        testUser.setEmail("user@test.com");
        when(reportRepository.findAll()).thenReturn(List.of(testReport));
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(verificationRepository.findVotesByUserIdAndReportIds(eq(1L), any()))
                .thenReturn(List.<Object[]>of(new Object[]{testReport.getReportId(), VoteType.AGREE}));

        List<ReportResponse> result = reportService.getAll("user@test.com");

        assertEquals(1, result.size());
        assertEquals(VoteType.AGREE, result.get(0).getUserVote());
        // Verify batch query is used exactly once (no N+1)
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
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByParentId(6L)).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        ReportResponse result = reportService.create(testRequest);

        assertNotNull(result);
        assertEquals("Broken ramp", result.getDescription());
        assertEquals(ReportStatus.PENDING, result.getStatus());
        assertEquals(6L, result.getCategoryId());
        verify(reportRepository).save(any(Report.class));
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
    void create_nonLeafCategory_throws400() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByParentId(6L)).thenReturn(true); // has children → not leaf

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
        verify(reportRepository, never()).delete(any());
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
        verify(reportRepository, never()).delete(any());
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
    void verifyReport_reachesThreshold_changesStatusToVerified() {
        ReflectionTestUtils.setField(testReport, "agrees", 4); // 4 agrees + 1 new agree = 5 (Threshold)
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
        ReflectionTestUtils.setField(testReport, "agrees", 1); // 1 agree + 1 new agree = 2 (Below threshold)
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
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(1, testReport.getDisagrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(reportRepository).save(testReport);
        verify(publicSseService).broadcastReportUpdated(testReport, "unverify");
    }

    @Test
    void create_withMeasurements_usesInheritedParentSchema() {
        ReportCategory parent = new ReportCategory();
        parent.setMeasurementSchema("{\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":100}}");

        ReportCategory child = new ReportCategory();
        ReflectionTestUtils.setField(child, "id", 6L);
        child.setName("Too Narrow");
        child.setType(ReportType.OBSTACLE);
        child.setParent(parent);

        testRequest.setMeasurements("{\"width_cm\":80}");

        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(child));
        when(categoryRepository.existsByParentId(6L)).thenReturn(false);
        when(measurementValidator.validate(any(), any())).thenReturn(List.of());
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        reportService.create(testRequest);

        verify(measurementValidator).validate(
                "{\"width_cm\":80}",
                "{\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":100}}"
        );
    }

    @Test
    void create_withMeasurements_usesOwnSchemaWhenChildHasOne() {
        ReportCategory parent = new ReportCategory();
        parent.setMeasurementSchema("{\"width_cm\":{\"min\":0,\"max\":500}}");

        ReportCategory child = new ReportCategory();
        ReflectionTestUtils.setField(child, "id", 6L);
        child.setName("High Threshold");
        child.setType(ReportType.OBSTACLE);
        child.setParent(parent);
        child.setMeasurementSchema("{\"height_cm\":{\"min\":0,\"max\":30,\"accessible_max\":1.3}}");

        testRequest.setMeasurements("{\"height_cm\":5}");

        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(child));
        when(categoryRepository.existsByParentId(6L)).thenReturn(false);
        when(measurementValidator.validate(any(), any())).thenReturn(List.of());
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        reportService.create(testRequest);

        verify(measurementValidator).validate(
                "{\"height_cm\":5}",
                "{\"height_cm\":{\"min\":0,\"max\":30,\"accessible_max\":1.3}}"
        );
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
    void unverifyReport_revertsVerifiedStatusToPending() {
        ReflectionTestUtils.setField(testReport, "agrees", 4);
        ReflectionTestUtils.setField(testReport, "status", ReportStatus.VERIFIED);
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);
        testUser.setEmail("user@test.com");

        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(verificationRepository.findByUserIdAndReportReportId(1L, 1L)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        reportService.unverifyReport(1L, "user@test.com");

        assertEquals(ReportStatus.PENDING, testReport.getStatus());
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
}
