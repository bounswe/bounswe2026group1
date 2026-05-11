package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.ReportService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
class ReportFeedWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    @Test
    void feed_get_returnsPagedJson_forAnonymousGlobalFeed() throws Exception {
        when(reportService.feed(any(ReportFeedQuery.class), any(Pageable.class), isNull()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/reports/feed")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<ReportFeedQuery> captor = ArgumentCaptor.forClass(ReportFeedQuery.class);
        verify(reportService).feed(captor.capture(), any(Pageable.class), isNull());
        ReportFeedQuery captured = captor.getValue();
        assertNull(captured.getReportType());
        assertNull(captured.getEnvironment());
        assertNull(captured.getLatitude());
        assertNull(captured.getLongitude());
    }

    @Test
    void feed_get_passesFiltersAndProximityParamsToService() throws Exception {
        ReportResponse row = new ReportResponse();
        row.setReportId(7L);
        when(reportService.feed(any(ReportFeedQuery.class), any(Pageable.class), isNull()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 15), 1));

        mockMvc.perform(get("/api/reports/feed")
                        .param("page", "1")
                        .param("size", "15")
                        .param("reportType", "OBSTACLE")
                        .param("environment", "OUTDOOR")
                        .param("status", "VERIFIED", "PENDING")
                        .param("authorId", "42")
                        .param("q", "ramp")
                        .param("minAgrees", "3")
                        .param("minNetScore", "1")
                        .param("sort", "MOST_AGREED")
                        .param("latitude", "41.02")
                        .param("longitude", "29.01")
                        .param("radiusInKm", "3.5")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].reportId").value(7));

        ArgumentCaptor<ReportFeedQuery> captor = ArgumentCaptor.forClass(ReportFeedQuery.class);
        verify(reportService).feed(captor.capture(), any(Pageable.class), isNull());
        ReportFeedQuery captured = captor.getValue();
        assertEquals(ReportType.OBSTACLE, captured.getReportType());
        assertEquals(ReportEnvironment.OUTDOOR, captured.getEnvironment());
        assertEquals(List.of(ReportStatus.VERIFIED, ReportStatus.PENDING), captured.getStatus());
        assertEquals(42L, captured.getAuthorId());
        assertEquals("ramp", captured.getQ());
        assertEquals(3, captured.getMinAgrees());
        assertEquals(1, captured.getMinNetScore());
        assertEquals(41.02, captured.getLatitude());
        assertEquals(29.01, captured.getLongitude());
        assertEquals(3.5, captured.getRadiusInKm());
    }

    @Test
    void feed_get_returnsBadRequestWhenServiceRejectsPartialCoordinates() throws Exception {
        when(reportService.feed(any(ReportFeedQuery.class), any(Pageable.class), isNull()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "latitude and longitude must both be provided for proximity feed"));

        mockMvc.perform(get("/api/reports/feed")
                        .param("latitude", "41")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isBadRequest());
    }
}
