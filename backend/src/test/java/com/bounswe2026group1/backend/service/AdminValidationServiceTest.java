package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminValidationServiceTest {

    @Mock private ReportVerificationRepository verificationRepository;
    @Mock private ReportRepository reportRepository;

    @InjectMocks
    private AdminValidationService adminValidationService;

    private Report report;
    private RegisteredUser voter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminValidationService, "verificationThreshold", 5);

        voter = new RegisteredUser();
        voter.setId(10L);

        report = new Report(null, GeoUtils.point4326(41.0, 29.0), "x",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        report.setReportId(50L);
        report.setStatus(ReportStatus.VERIFIED);
        report.setAgrees(5);
    }

    @Test
    void deleteValidation_decrementsAgree_andDowngradesWhenBelowThreshold() {
        ReportVerification v = new ReportVerification(voter, report, VoteType.AGREE);
        ReflectionTestUtils.setField(v, "id", 100L);
        when(verificationRepository.findById(100L)).thenReturn(Optional.of(v));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        adminValidationService.deleteValidation(100L);

        assertEquals(4, report.getAgrees());
        assertEquals(ReportStatus.PENDING, report.getStatus());
        verify(verificationRepository).delete(v);
        verify(reportRepository).save(report);
    }

    @Test
    void deleteValidation_throwsWhenMissing() {
        when(verificationRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> adminValidationService.deleteValidation(1L));
    }

    @Test
    void deleteValidation_disagreeBranch() {
        ReportVerification v = new ReportVerification(voter, report, VoteType.DISAGREE);
        ReflectionTestUtils.setField(v, "id", 101L);
        report.setDisagrees(2);
        when(verificationRepository.findById(101L)).thenReturn(Optional.of(v));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        adminValidationService.deleteValidation(101L);

        assertEquals(1, report.getDisagrees());
        verify(verificationRepository).delete(v);
    }
}
