package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    @GetMapping
    public List<ReportResponse> getAll() {
        logger.info("GET /api/reports - fetching all reports");
        return reportService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getById(@PathVariable Long id) {
        logger.info("GET /api/reports/{} - fetching report by id", id);
        return reportService.getById(id)
                .map(r -> {
                    logger.info("Report found with id: {} - 200 OK", id);
                    return ResponseEntity.ok(r);
                })
                .orElseGet(() -> {
                    logger.warn("Report not found with id: {} - 404 NOT FOUND", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/user/{userId}")
    public List<ReportResponse> getByUserId(@PathVariable Long userId) {
        logger.info("GET /api/reports/user/{} - fetching reports by user", userId);
        return reportService.getByUserId(userId);
    }

    @PostMapping
    public ResponseEntity<ReportResponse> create(@RequestBody CreateReportRequest request) {
        logger.info("POST /api/reports - creating new report");
        ReportResponse response = reportService.create(request);
        logger.info("Report created with id: {} - 201 CREATED", response.getReportId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportResponse> update(@PathVariable Long id, @RequestBody CreateReportRequest request) {
        logger.info("PUT /api/reports/{} - updating report", id);
        return reportService.update(id, request)
                .map(r -> {
                    logger.info("Report updated with id: {} - 200 OK", id);
                    return ResponseEntity.ok(r);
                })
                .orElseGet(() -> {
                    logger.warn("Report not found for update with id: {} - 404 NOT FOUND", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/reports/{} - deleting report", id);
        if (reportService.delete(id)) {
            logger.info("Report deleted with id: {} - 204 NO CONTENT", id);
            return ResponseEntity.noContent().build();
        }
        logger.warn("Report not found for deletion with id: {} - 404 NOT FOUND", id);
        return ResponseEntity.notFound().build();
    }
}
