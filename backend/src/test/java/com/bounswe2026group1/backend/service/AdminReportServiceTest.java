package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.ReportRepository;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private S3MediaService s3MediaService;
    @Mock private PublicSseService publicSseService;

    @InjectMocks
    private AdminReportService adminReportService;

    private Report report;
    private RegisteredUser owner;

    @BeforeEach
    void setUp() {
        owner = new RegisteredUser();
        owner.setId(2L);
        owner.setEmail("owner@test.com");

        report = new Report(owner, GeoUtils.point4326(41.0, 29.0), "x",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        report.setReportId(7L);
        report.setStatus(ReportStatus.PENDING);
    }

    @Test
    void listReports_mapsPage() {
        Pageable p = PageRequest.of(0, 10);
        when(reportRepository.findAll(any(Specification.class), eq(p)))
                .thenReturn(new PageImpl<>(List.of(report)));

        Page<ReportResponse> page = adminReportService.listReports(
                null, null, null, null, null, null, p);

        assertEquals(1, page.getTotalElements());
        assertEquals(7L, page.getContent().getFirst().getReportId());
    }

    @Test
    void changeStatus_updatesAndSaves() {
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse res = adminReportService.changeStatus(7L, ReportStatus.VERIFIED);

        assertEquals(ReportStatus.VERIFIED, report.getStatus());
        assertEquals(7L, res.getReportId());
    }

    @Test
    void changeStatus_throwsWhenMissing() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> adminReportService.changeStatus(99L, ReportStatus.VERIFIED));
    }

    @Test
    void deleteReport_removesMediaAndBroadcasts() {
        Media m = new Media();
        m.setFilePath("key/photo.jpg");
        report.setMediaList(List.of(m));

        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        adminReportService.deleteReport(7L);

        verify(s3MediaService).deleteFile("key/photo.jpg");
        verify(reportRepository).delete(report);
        verify(publicSseService).broadcastReportDeleted(7L);
    }
}
