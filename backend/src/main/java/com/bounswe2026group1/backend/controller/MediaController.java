package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.service.ReportService;
import com.bounswe2026group1.backend.service.S3MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MediaController {

    private final S3MediaService s3MediaService;
    private final ReportService reportService;

    @PostMapping(value = "/{id}/media", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMediaForReport(
            @PathVariable("id") Long reportId,
            @RequestParam("file") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("No files provided.");
        }

        List<String> uploadedUrls = new ArrayList<>();
        try {
            // 1. Validate all files before any network I/O
            for (MultipartFile file : files) {
                s3MediaService.validate(file);
            }

            // 2. Upload all files to S3
            for (MultipartFile file : files) {
                uploadedUrls.add(s3MediaService.uploadFile(file));
            }

            // 3. Link all uploaded files to the database
            for (String mediaUrl : uploadedUrls) {
                reportService.addMediaToReport(reportId, mediaUrl);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mediaUrl", uploadedUrls.isEmpty() ? null : uploadedUrls.get(0),
                "mediaUrls", uploadedUrls
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e){
            // If report is not found, delete any files we just uploaded to S3
            for (String url : uploadedUrls) {
                try { s3MediaService.deleteFile(url); } catch (Exception ignored) {}
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // If any upload or DB operation fails midway, rollback S3 uploads
            for (String url : uploadedUrls) {
                try { s3MediaService.deleteFile(url); } catch (Exception ignored) {}
            }
            return ResponseEntity.internalServerError().body("Upload failed.");
        }
    }
}