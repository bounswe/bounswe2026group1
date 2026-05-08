package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.service.ReportService;
import com.bounswe2026group1.backend.service.S3MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Media", description = "Upload report media to S3 and attach it to reports.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class MediaController {

    private final S3MediaService s3MediaService;
    private final ReportService reportService;

    @PostMapping("/{id}/media")
    @Operation(
            summary = "Attach a media file to a report",
            description = "Multipart upload (field `file`). Per-file cap 15 MB; up to 5 files " +
                    "per report. The file is uploaded to S3 and the resulting URL is linked to " +
                    "the report."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Media uploaded; returns `{ mediaUrl }`."),
            @ApiResponse(responseCode = "400", description = "Invalid or oversized file."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No report with that id."),
            @ApiResponse(responseCode = "500", description = "Upload failed (e.g. S3 unavailable).")
    })
    public ResponseEntity<?> uploadMediaForReport(
            @PathVariable("id") Long reportId,
            @RequestParam("file") MultipartFile file) {

        try {
            // Sends the file to AWS S3 and gets the public URL back
            String mediaUrl = s3MediaService.uploadFile(file);
            // Saves that URL to the database by linking it to a specified report
            reportService.addMediaToReport(reportId, mediaUrl);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mediaUrl", mediaUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload failed.");
        }
    }
}
