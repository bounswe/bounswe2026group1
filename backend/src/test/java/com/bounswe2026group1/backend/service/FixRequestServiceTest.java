package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.model.FixRequest;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.FixRequestVote;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.FixRequestRepository;
import com.bounswe2026group1.backend.repository.FixRequestVoteRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixRequestServiceTest {

    @Mock private FixRequestRepository fixRequestRepository;
    @Mock private FixRequestVoteRepository fixRequestVoteRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private S3MediaService s3MediaService;
    @Mock private PublicSseService publicSseService;

    @InjectMocks
    private FixRequestService fixRequestService;

    private RegisteredUser submitter;
    private RegisteredUser voter;
    private Report parentReport;
    private MultipartFile validPhoto;

    @BeforeEach
    void setUp() {
        submitter = new RegisteredUser();
        submitter.setId(2L);
        submitter.setEmail("submitter@test.com");
        submitter.setName("Mehmet O.");

        voter = new RegisteredUser();
        voter.setId(3L);
        voter.setEmail("voter@test.com");

        RegisteredUser owner = new RegisteredUser();
        owner.setId(1L);
        owner.setEmail("owner@test.com");
        owner.setName("Ayşe K.");
        parentReport = new Report(owner, new Location(41.0, 29.0), "Missing ramp", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReflectionTestUtils.setField(parentReport, "reportId", 100L);
        ReflectionTestUtils.setField(parentReport, "status", ReportStatus.VERIFIED);

        validPhoto = new MockMultipartFile("files", "fix.jpg", "image/jpeg", "bytes".getBytes());

        ReflectionTestUtils.setField(fixRequestService, "fixThreshold", 5);
        ReflectionTestUtils.setField(fixRequestService, "fixRatio", 0.60);
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    void submit_validRequest_returnsCreatedResponseAndBroadcasts() {
        when(registeredUserRepository.findByEmail("submitter@test.com")).thenReturn(Optional.of(submitter));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(parentReport));
        when(fixRequestRepository.findFirstByReportReportIdAndState(100L, FixRequestState.OPEN))
                .thenReturn(Optional.empty());
        when(s3MediaService.uploadFile(any())).thenReturn("https://bucket.s3.amazonaws.com/fix.jpg");
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        FixRequestResponse response = fixRequestService.submit(
                100L, "submitter@test.com", "Concrete ramp poured", new MultipartFile[]{validPhoto});

        assertNotNull(response);
        assertEquals(FixRequestState.OPEN, response.getState());
        assertEquals(2L, response.getSubmittedByUserId());
        assertEquals("Concrete ramp poured", response.getDescription());
        verify(s3MediaService).uploadFile(validPhoto);
        verify(publicSseService).broadcastFixRequested(parentReport);
    }

    @Test
    void submit_noFiles_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, "submitter@test.com", "anything", new MultipartFile[0]));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(reportRepository, fixRequestRepository, s3MediaService);
    }

    @Test
    void submit_emptyFile_throws400AndDoesNotUpload() {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);
        doThrow(new IllegalArgumentException("Invalid file type.")).when(s3MediaService).validate(any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, "submitter@test.com", "x", new MultipartFile[]{emptyFile}));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        // Pre-validation must run before any upload, user lookup, or DB writes.
        verify(s3MediaService, never()).uploadFile(any());
        verifyNoInteractions(reportRepository, fixRequestRepository, registeredUserRepository);
    }

    @Test
    void submit_secondFileInvalid_throws400AndDoesNotUploadEither() {
        MockMultipartFile good = new MockMultipartFile("files", "ok.jpg", "image/jpeg", "bytes".getBytes());
        MockMultipartFile bad = new MockMultipartFile("files", "doc.pdf", "application/pdf", "bytes".getBytes());
        doNothing().when(s3MediaService).validate(good);
        doThrow(new IllegalArgumentException("Invalid file type.")).when(s3MediaService).validate(bad);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, "submitter@test.com", "x", new MultipartFile[]{good, bad}));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        // Critical: even the good file must not be uploaded, since a partial-batch failure
        // would leak the good file on S3 (the rolled-back transaction can't clean S3).
        verify(s3MediaService, never()).uploadFile(any());
    }

    @Test
    void submit_oversizedDescription_throws400() {
        String tooLong = "a".repeat(1001);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, "submitter@test.com", tooLong, new MultipartFile[]{validPhoto}));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(reportRepository, fixRequestRepository, registeredUserRepository);
        verify(s3MediaService, never()).uploadFile(any());
        verify(s3MediaService, never()).validate(any());
    }

    @Test
    void submit_descriptionAtMaxLength_succeeds() {
        String exactlyMax = "a".repeat(1000);
        when(registeredUserRepository.findByEmail("submitter@test.com")).thenReturn(Optional.of(submitter));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(parentReport));
        when(fixRequestRepository.findFirstByReportReportIdAndState(100L, FixRequestState.OPEN))
                .thenReturn(Optional.empty());
        when(s3MediaService.uploadFile(any())).thenReturn("https://bucket.s3.amazonaws.com/fix.jpg");
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        FixRequestResponse response = fixRequestService.submit(
                100L, "submitter@test.com", exactlyMax, new MultipartFile[]{validPhoto});

        assertEquals(exactlyMax, response.getDescription());
    }

    @Test
    void submit_anonymous_throws401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, null, "x", new MultipartFile[]{validPhoto}));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void submit_reportNotFound_throws404() {
        when(registeredUserRepository.findByEmail("submitter@test.com")).thenReturn(Optional.of(submitter));
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(99L, "submitter@test.com", null, new MultipartFile[]{validPhoto}));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void submit_existingOpenFixRequest_throws409() {
        FixRequest existing = new FixRequest(parentReport, submitter, "earlier");
        when(registeredUserRepository.findByEmail("submitter@test.com")).thenReturn(Optional.of(submitter));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(parentReport));
        when(fixRequestRepository.findFirstByReportReportIdAndState(100L, FixRequestState.OPEN))
                .thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.submit(100L, "submitter@test.com", "x", new MultipartFile[]{validPhoto}));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(s3MediaService, never()).uploadFile(any());
    }

    // ── voting ────────────────────────────────────────────────────────────────

    @Test
    void agree_firstVote_incrementsAgrees() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L)).thenReturn(Optional.empty());
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        FixRequestResponse response = fixRequestService.agree(50L, "voter@test.com");

        assertEquals(1, response.getAgrees());
        assertEquals(0, response.getDisagrees());
        verify(fixRequestVoteRepository).save(any(FixRequestVote.class));
    }

    @Test
    void agree_toggleOff_decrementsAndDeletesVote() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        fr.incrementAgrees();
        FixRequestVote existing = new FixRequestVote(voter, fr, VoteType.AGREE);
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        FixRequestResponse response = fixRequestService.agree(50L, "voter@test.com");

        assertEquals(0, response.getAgrees());
        verify(fixRequestVoteRepository).delete(existing);
    }

    @Test
    void disagree_switchFromAgree_movesCount() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        fr.incrementAgrees();
        FixRequestVote existing = new FixRequestVote(voter, fr, VoteType.AGREE);
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(new FixRequestVote(voter, fr, VoteType.DISAGREE)));
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        FixRequestResponse response = fixRequestService.disagree(50L, "voter@test.com");

        assertEquals(0, response.getAgrees());
        assertEquals(1, response.getDisagrees());
        assertEquals(VoteType.DISAGREE, existing.getVoteType());
    }

    @Test
    void agree_thresholdReached_transitionsReportToFixedAndBroadcasts() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        // 4 existing agrees + this 5th = 5, ratio = 5/5 = 100% (above 60%)
        for (int i = 0; i < 4; i++) fr.incrementAgrees();
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L)).thenReturn(Optional.empty());
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        fixRequestService.agree(50L, "voter@test.com");

        assertEquals(FixRequestState.RESOLVED_FIXED, fr.getState());
        assertNotNull(fr.getResolvedAt());
        assertEquals(ReportStatus.FIXED, parentReport.getStatus());
        assertNotNull(parentReport.getFixedAt());
        verify(publicSseService).broadcastFixed(parentReport);
    }

    @Test
    void agree_thresholdMetButRatioFails_staysOpen() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        // Start: 4 agrees + 4 disagrees. New agree -> 5 agrees + 4 disagrees = 5/9 ≈ 55% (below 60%)
        for (int i = 0; i < 4; i++) fr.incrementAgrees();
        for (int i = 0; i < 4; i++) fr.incrementDisagrees();
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L)).thenReturn(Optional.empty());
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        fixRequestService.agree(50L, "voter@test.com");

        assertEquals(FixRequestState.OPEN, fr.getState());
        assertEquals(ReportStatus.VERIFIED, parentReport.getStatus());
        verify(publicSseService, never()).broadcastFixed(any());
    }

    @Test
    void agree_belowCountThreshold_staysOpen() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        for (int i = 0; i < 3; i++) fr.incrementAgrees();
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(fixRequestVoteRepository.findByUserIdAndFixRequestId(3L, 50L)).thenReturn(Optional.empty());
        when(fixRequestRepository.save(any(FixRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        fixRequestService.agree(50L, "voter@test.com");

        assertEquals(4, fr.getAgrees());
        assertEquals(FixRequestState.OPEN, fr.getState());
        verify(publicSseService, never()).broadcastFixed(any());
    }

    @Test
    void agree_onResolvedFixRequest_throws409() {
        FixRequest fr = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(fr, "id", 50L);
        fr.setState(FixRequestState.RESOLVED_FIXED);
        when(registeredUserRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(fixRequestRepository.findById(50L)).thenReturn(Optional.of(fr));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fixRequestService.agree(50L, "voter@test.com"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ── scheduler hooks ───────────────────────────────────────────────────────

    @Test
    void expireOpenFixRequestsOlderThan_marksStaleRequestsExpired() {
        FixRequest stale = new FixRequest(parentReport, submitter, null);
        ReflectionTestUtils.setField(stale, "id", 60L);
        when(fixRequestRepository.findByStateAndCreatedAtBefore(eqState(FixRequestState.OPEN), any(Instant.class)))
                .thenReturn(List.of(stale));

        int count = fixRequestService.expireOpenFixRequestsOlderThan(Duration.ofDays(7));

        assertEquals(1, count);
        assertEquals(FixRequestState.EXPIRED, stale.getState());
        assertNotNull(stale.getResolvedAt());
        verify(fixRequestRepository).saveAll(List.of(stale));
    }

    @Test
    void deleteFixedReportsOlderThan_deletesReportAndBroadcasts() {
        ReflectionTestUtils.setField(parentReport, "status", ReportStatus.FIXED);
        ReflectionTestUtils.setField(parentReport, "fixedAt", Instant.now().minus(Duration.ofDays(8)));
        when(reportRepository.findByStatusAndFixedAtBefore(eq(ReportStatus.FIXED), any(Instant.class)))
                .thenReturn(List.of(parentReport));

        int count = fixRequestService.deleteFixedReportsOlderThan(Duration.ofDays(7));

        assertEquals(1, count);
        verify(reportRepository).delete(parentReport);
        verify(publicSseService).broadcastReportDeleted(100L);
    }

    private static FixRequestState eqState(FixRequestState s) { return eq(s); }
}
