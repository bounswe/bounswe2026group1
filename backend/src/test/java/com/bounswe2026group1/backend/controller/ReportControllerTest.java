package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Thin passthrough tests for {@link ReportController}, matching {@link NotificationControllerTest}. */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController controller;

    @Test
    void feed_passesQueryFieldsPageableAndEmailToService() {
        ReportFeedQuery query = new ReportFeedQuery();
        query.setReportType(ReportType.OBSTACLE);
        query.setEnvironment(ReportEnvironment.INDOOR);
        query.setLatitude(41.02);
        query.setLongitude(29.01);
        query.setRadiusInKm(4.5);

        Pageable pageable = PageRequest.of(1, 15);
        Page<ReportResponse> expected = new PageImpl<>(List.of());
        when(reportService.feed(
                ReportType.OBSTACLE,
                ReportEnvironment.INDOOR,
                41.02,
                29.01,
                4.5,
                pageable,
                "user@example.com")).thenReturn(expected);

        Page<ReportResponse> result = controller.feed(query, pageable, "user@example.com");

        assertEquals(expected, result);
        verify(reportService).feed(
                ReportType.OBSTACLE,
                ReportEnvironment.INDOOR,
                41.02,
                29.01,
                4.5,
                pageable,
                "user@example.com");
    }

    @Test
    void feed_passesNullPrincipalForAnonymousClients() {
        ReportFeedQuery query = new ReportFeedQuery();
        Pageable pageable = PageRequest.of(0, 20);
        Page<ReportResponse> expected = new PageImpl<>(List.of());
        when(reportService.feed(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(pageable),
                isNull())).thenReturn(expected);

        Page<ReportResponse> result = controller.feed(query, pageable, null);

        assertEquals(expected, result);
        verify(reportService).feed(null, null, null, null, null, pageable, null);
    }
}
