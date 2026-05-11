package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void getAll_passesPrincipalEmailToService() {
        ReportResponse one = new ReportResponse();
        List<ReportResponse> expected = List.of(one);
        when(reportService.getAll("caller@example.com")).thenReturn(expected);

        List<ReportResponse> result = controller.getAll("caller@example.com");

        assertSame(expected, result);
        verify(reportService).getAll("caller@example.com");
    }

    @Test
    void getAll_passesNullForAnonymousCaller() {
        when(reportService.getAll(null)).thenReturn(List.of());

        List<ReportResponse> result = controller.getAll(null);

        assertEquals(List.of(), result);
        verify(reportService).getAll(null);
    }

    @Test
    void getById_returns200WhenServiceFindsReport() {
        ReportResponse found = new ReportResponse();
        when(reportService.getById(99L, "u@example.com")).thenReturn(Optional.of(found));

        ResponseEntity<ReportResponse> result = controller.getById(99L, "u@example.com");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(found, result.getBody());
    }

    @Test
    void getById_returns404WhenServiceReturnsEmpty() {
        when(reportService.getById(404L, null)).thenReturn(Optional.empty());

        ResponseEntity<ReportResponse> result = controller.getById(404L, null);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNull(result.getBody());
    }

    @Test
    void getByUserId_passesIdToService() {
        ReportResponse one = new ReportResponse();
        when(reportService.getByUserId(42L)).thenReturn(List.of(one));

        List<ReportResponse> result = controller.getByUserId(42L);

        assertEquals(List.of(one), result);
        verify(reportService).getByUserId(42L);
    }

    @Test
    void create_returns201AndDelegatesToService() {
        CreateReportRequest request = new CreateReportRequest();
        ReportResponse created = new ReportResponse();
        when(reportService.create(request)).thenReturn(created);

        ResponseEntity<ReportResponse> result = controller.create(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(created, result.getBody());
        verify(reportService).create(request);
    }

    @Test
    void update_returns200WithUpdatedReport() {
        UpdateReportRequest request = new UpdateReportRequest();
        ReportResponse updated = new ReportResponse();
        when(reportService.update(7L, request, "owner@example.com")).thenReturn(updated);

        ResponseEntity<ReportResponse> result = controller.update(7L, request, "owner@example.com");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(updated, result.getBody());
        verify(reportService).update(7L, request, "owner@example.com");
    }

    @Test
    void delete_delegatesToService_returnsVoid() {
        controller.delete(13L, "owner@example.com");

        verify(reportService).delete(13L, "owner@example.com");
    }

    @Test
    void verifyReport_returnsServiceResultWith200() {
        ReportResponse afterVote = new ReportResponse();
        when(reportService.verifyReport(5L, "voter@example.com")).thenReturn(afterVote);

        ResponseEntity<ReportResponse> result = controller.verifyReport(5L, "voter@example.com");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(afterVote, result.getBody());
        verify(reportService).verifyReport(5L, "voter@example.com");
    }

    @Test
    void unverifyReport_returnsServiceResultWith200() {
        ReportResponse afterVote = new ReportResponse();
        when(reportService.unverifyReport(5L, "voter@example.com")).thenReturn(afterVote);

        ResponseEntity<ReportResponse> result = controller.unverifyReport(5L, "voter@example.com");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(afterVote, result.getBody());
        verify(reportService).unverifyReport(5L, "voter@example.com");
    }
}
