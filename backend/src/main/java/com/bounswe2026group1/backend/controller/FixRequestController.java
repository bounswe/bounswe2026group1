package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.service.FixRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reports/{reportId}/fix-requests")
@RequiredArgsConstructor
public class FixRequestController {

    private final FixRequestService fixRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FixRequestResponse> submit(
            @PathVariable Long reportId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal String email) {
        FixRequestResponse response = fixRequestService.submit(reportId, email, description, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{fixId}/agree")
    public ResponseEntity<FixRequestResponse> agree(
            @PathVariable Long reportId,
            @PathVariable Long fixId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(fixRequestService.agree(fixId, email));
    }

    @PostMapping("/{fixId}/disagree")
    public ResponseEntity<FixRequestResponse> disagree(
            @PathVariable Long reportId,
            @PathVariable Long fixId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(fixRequestService.disagree(fixId, email));
    }
}
