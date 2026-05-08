package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.service.ReportService;
import com.bounswe2026group1.backend.service.S3MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MediaController {

    private final S3MediaService s3MediaService;
    private final ReportService reportService;

    @PostMapping("/{id}/media")
    public ResponseEntity<?> uploadMediaForReport(
            @PathVariable("id") Long reportId,
            @RequestParam("file") MultipartFile file) {

        try {
            // Sends the file to AWS S3 and gets the public URL back
            String mediaUrl = s3MediaService.uploadFile(file);
            // Saves that URL to the database by linking it to a specified report
            com.bounswe2026group1.backend.model.Media media = reportService.addMediaToReport(reportId, mediaUrl);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mediaId", media.getId(),
                    "mediaUrl", mediaUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload failed.");
        }
    }
}