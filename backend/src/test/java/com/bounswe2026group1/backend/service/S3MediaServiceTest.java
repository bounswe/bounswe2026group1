package com.bounswe2026group1.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3MediaServiceTest {

    @Mock private S3Client s3Client;

    private S3MediaService s3MediaService;

    private static final String BUCKET_NAME = "test-bucket";

    @BeforeEach
    void setUp() {
        s3MediaService = new S3MediaService(s3Client, BUCKET_NAME);
    }

    // ── Successful uploads ────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFile: valid JPEG returns a public S3 URL")
    void uploadFile_validJpeg_returnsUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = s3MediaService.uploadFile(file);

        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
        assertTrue(url.contains(BUCKET_NAME));
        assertTrue(url.contains("photo.jpg"));
    }

    @Test
    @DisplayName("uploadFile: valid PNG returns a public S3 URL")
    void uploadFile_validPng_returnsUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", "fake-image-bytes".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = s3MediaService.uploadFile(file);

        assertTrue(url.contains("image.png"));
    }

    @Test
    @DisplayName("uploadFile: valid MP4 returns a public S3 URL")
    void uploadFile_validMp4_returnsUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "fake-video-bytes".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = s3MediaService.uploadFile(file);

        assertTrue(url.contains("clip.mp4"));
    }

    @Test
    @DisplayName("uploadFile: each upload gets a unique file name (UUID prefix)")
    void uploadFile_uniqueFileNames() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url1 = s3MediaService.uploadFile(file);
        String url2 = s3MediaService.uploadFile(file);

        assertNotEquals(url1, url2);
    }

    @Test
    @DisplayName("uploadFile: actually calls S3 putObject once")
    void uploadFile_callsS3PutObject() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        s3MediaService.uploadFile(file);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // ── Rejected file types ───────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFile: unsupported content type throws IllegalArgumentException")
    void uploadFile_unsupportedType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "bytes".getBytes());

        assertThrows(IllegalArgumentException.class, () -> s3MediaService.uploadFile(file));
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("uploadFile: GIF content type throws IllegalArgumentException")
    void uploadFile_gif_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "anim.gif", "image/gif", "bytes".getBytes());

        assertThrows(IllegalArgumentException.class, () -> s3MediaService.uploadFile(file));
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("uploadFile: null content type throws IllegalArgumentException")
    void uploadFile_nullContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", null, "bytes".getBytes());

        assertThrows(IllegalArgumentException.class, () -> s3MediaService.uploadFile(file));
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("uploadFile: empty file throws IllegalArgumentException")
    void uploadFile_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> s3MediaService.uploadFile(file));
        verifyNoInteractions(s3Client);
    }
}