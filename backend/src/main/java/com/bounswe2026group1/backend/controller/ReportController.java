package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public List<ReportResponse> getAll() {
        return reportService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getById(@PathVariable Long id) {
        return reportService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<ReportResponse> update(@PathVariable Long id, @RequestBody CreateReportRequest request) {
        return reportService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return reportService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
