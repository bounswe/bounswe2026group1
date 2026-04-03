package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.service.ReportService;
import com.bounswe2026group1.backend.service.S3MediaService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MediaController.class)
class MediaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private S3MediaService s3MediaService;  // @MockBean removed in Spring Boot 4.x
    @MockitoBean private ReportService  reportService;
    @MockitoBean private JwtUtil jwtUtil;

    private static final String UPLOAD_URL = "/api/reports/{id}/media";
    private static final String RETURNED_URL = "https://test-bucket.s3.amazonaws.com/uuid_photo.jpg";

    // ── 201 Created ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/media: valid JPEG returns 201 with mediaUrl")
    void upload_validJpeg_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());

        when(s3MediaService.uploadFile(any())).thenReturn(RETURNED_URL);
        doNothing().when(reportService).addMediaToReport(eq(1L), eq(RETURNED_URL));

        mockMvc.perform(multipart(UPLOAD_URL, 1L).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaUrl").value(RETURNED_URL));
    }

    @Test
    @DisplayName("POST /{id}/media: mediaUrl in response matches the URL returned by S3")
    void upload_responseContainsCorrectUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.png", "image/png", "bytes".getBytes());
        String expectedUrl = "https://bucket.s3.amazonaws.com/some-uuid_pic.png";

        when(s3MediaService.uploadFile(any())).thenReturn(expectedUrl);

        mockMvc.perform(multipart(UPLOAD_URL, 5L).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaUrl").value(expectedUrl));
    }

    @Test
    @DisplayName("POST /{id}/media: addMediaToReport is called with correct reportId and URL")
    void upload_linksMediaToReport() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        when(s3MediaService.uploadFile(any())).thenReturn(RETURNED_URL);

        mockMvc.perform(multipart(UPLOAD_URL, 42L).file(file))
                .andExpect(status().isCreated());

        verify(reportService, times(1)).addMediaToReport(42L, RETURNED_URL);
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/media: unsupported file type returns 400")
    void upload_unsupportedType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "bytes".getBytes());

        when(s3MediaService.uploadFile(any()))
                .thenThrow(new IllegalArgumentException("Invalid file type."));

        mockMvc.perform(multipart(UPLOAD_URL, 1L).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/media: empty file returns 400")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        when(s3MediaService.uploadFile(any()))
                .thenThrow(new IllegalArgumentException("Invalid file type."));

        mockMvc.perform(multipart(UPLOAD_URL, 1L).file(file))
                .andExpect(status().isBadRequest());
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/media: non-existent report returns 404")
    void upload_reportNotFound_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        when(s3MediaService.uploadFile(any())).thenReturn(RETURNED_URL);
        doThrow(new NoSuchElementException("Report not found with id: 99"))
                .when(reportService).addMediaToReport(eq(99L), any());

        mockMvc.perform(multipart(UPLOAD_URL, 99L).file(file))
                .andExpect(status().isNotFound());
    }

    // ── S3 failure ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/media: S3 failure returns 500")
    void upload_s3Failure_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        when(s3MediaService.uploadFile(any()))
                .thenThrow(new RuntimeException("Failed to upload to S3"));

        mockMvc.perform(multipart(UPLOAD_URL, 1L).file(file))
                .andExpect(status().isInternalServerError());
    }
}