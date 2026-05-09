package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public List<ReportResponse> getAll(@AuthenticationPrincipal String email) {
        return reportService.getAll(email);
    }

    @GetMapping("/feed")
    public Page<ReportResponse> feed(
            ReportFeedQuery query,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal String email) {
        return reportService.feed(
                query.getReportType(),
                query.getEnvironment(),
                query.getLatitude(),
                query.getLongitude(),
                query.getRadiusInKm(),
                pageable,
                email);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getById(@PathVariable Long id,
                                                   @AuthenticationPrincipal String email) {
        return reportService.getById(id, email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<ReportResponse> getByUserId(@PathVariable Long userId) {
        return reportService.getByUserId(userId);
    }

    @PostMapping
    public ResponseEntity<ReportResponse> create(@RequestBody CreateReportRequest request) {
        ReportResponse response = reportService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateReportRequest request,
                                                  @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.update(id, request, email));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal String email) {
        reportService.delete(id, email);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ReportResponse> verifyReport(@PathVariable Long id,
                                                       @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.verifyReport(id, email));
    }

    @PostMapping("/{id}/unverify")
    public ResponseEntity<ReportResponse> unverifyReport(@PathVariable Long id,
                                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.unverifyReport(id, email));
    }

}
