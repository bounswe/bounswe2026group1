package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @InjectMocks
    private ReportService reportService;

    private RegisteredUser testUser;
    private Report testReport;
    private CreateReportRequest testRequest;

    @BeforeEach
    void setUp() {
        testUser = new RegisteredUser();
        testUser.setId(1L);

        testReport = new Report(testUser, new Location(41.0, 29.0), "Broken ramp", Tag.MISSING_RAMP);

        testRequest = new CreateReportRequest();
        testRequest.setUserId(1L);
        testRequest.setLatitude(41.0);
        testRequest.setLongitude(29.0);
        testRequest.setDescription("Broken ramp");
        testRequest.setTag(Tag.MISSING_RAMP);
    }

    @Test
    void getAll_returnsAllReports() {
        when(reportRepository.findAll()).thenReturn(List.of(testReport));

        List<ReportResponse> result = reportService.getAll();

        assertEquals(1, result.size());
        assertEquals("Broken ramp", result.get(0).getDescription());
        verify(reportRepository).findAll();
    }

    @Test
    void getById_existingId_returnsReport() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));

        Optional<ReportResponse> result = reportService.getById(1L);

        assertTrue(result.isPresent());
        assertEquals("Broken ramp", result.get().getDescription());
    }

    @Test
    void getById_nonExistingId_returnsEmpty() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<ReportResponse> result = reportService.getById(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void create_validRequest_returnsCreatedReport() {
        when(registeredUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        ReportResponse result = reportService.create(testRequest);

        assertNotNull(result);
        assertEquals("Broken ramp", result.getDescription());
        assertEquals(Tag.MISSING_RAMP, result.getTag());
        assertEquals(ReportStatus.PENDING, result.getStatus());
        verify(reportRepository).save(any(Report.class));
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
    void update_existingId_returnsUpdatedReport() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(reportRepository.save(any(Report.class))).thenReturn(testReport);

        testRequest.setDescription("Updated description");
        Optional<ReportResponse> result = reportService.update(1L, testRequest);

        assertTrue(result.isPresent());
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void delete_existingId_returnsTrue() {
        when(reportRepository.existsById(1L)).thenReturn(true);

        boolean result = reportService.delete(1L);

        assertTrue(result);
        verify(reportRepository).deleteById(1L);
    }

    @Test
    void delete_nonExistingId_returnsFalse() {
        when(reportRepository.existsById(99L)).thenReturn(false);

        boolean result = reportService.delete(99L);

        assertFalse(result);
        verify(reportRepository, never()).deleteById(any());
    }

    @Test
    void verifyReport_reachesThreshold_changesStatusToVerified() {
        // Arrange
        ReflectionTestUtils.setField(testReport, "agrees", 4); // 4 agrees + 1 new agree = 5 (Threshold)
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ReportResponse result = reportService.verifyReport(1L);

        // Assert
        assertEquals(5, testReport.getAgrees());
        assertEquals(ReportStatus.VERIFIED, testReport.getStatus());
        verify(reportRepository).save(testReport);
    }

    @Test
    void verifyReport_belowThreshold_statusRemainsPending() {
        ReflectionTestUtils.setField(testReport, "agrees", 1); // 1 agree + 1 new agree = 2 (Below threshold)
        ReflectionTestUtils.setField(reportService, "verificationThreshold", 5);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        ReportResponse result = reportService.verifyReport(1L);

        assertEquals(2, testReport.getAgrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus());
        verify(reportRepository).save(testReport);
    }

    @Test
    void unverifyReport_incrementsDisagrees() {
        ReflectionTestUtils.setField(testReport, "disagrees", 0);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArguments()[0]);

        ReportResponse result = reportService.unverifyReport(1L);

        assertEquals(1, testReport.getDisagrees());
        assertEquals(ReportStatus.PENDING, testReport.getStatus()); // Unverifying should not change status
        verify(reportRepository).save(testReport);
    }

}
