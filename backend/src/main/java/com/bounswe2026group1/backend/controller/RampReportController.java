package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateRampReportRequest;
import com.bounswe2026group1.backend.dto.RampReportResponse;
import com.bounswe2026group1.backend.service.RampReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports/ramp")
@RequiredArgsConstructor
public class RampReportController {

    private final RampReportService rampReportService;

    @PostMapping
    public ResponseEntity<RampReportResponse> create(@RequestBody CreateRampReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rampReportService.create(request));
    }

    @GetMapping
    public List<RampReportResponse> getAll() {
        return rampReportService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RampReportResponse> getById(@PathVariable Long id) {
        return rampReportService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
