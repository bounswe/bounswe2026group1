package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminStatsResponse;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private RegisteredUserRepository userRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    void getStats_aggregatesRepositoryCounts() {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(90L);
        when(userRepository.countByStatus(UserStatus.BANNED)).thenReturn(3L);
        when(reportRepository.count()).thenReturn(50L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(10L);
        when(reportRepository.countByStatus(ReportStatus.VERIFIED)).thenReturn(30L);
        when(commentRepository.count()).thenReturn(200L);

        AdminStatsResponse r = adminStatsService.getStats();

        assertEquals(100, r.getTotalUsers());
        assertEquals(90, r.getActiveUsers());
        assertEquals(3, r.getBannedUsers());
        assertEquals(50, r.getTotalReports());
        assertEquals(10, r.getPendingReports());
        assertEquals(30, r.getVerifiedReports());
        assertEquals(200, r.getTotalComments());
    }
}
