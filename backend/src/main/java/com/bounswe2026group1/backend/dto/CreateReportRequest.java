package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    private Long userId;
    private double latitude;
    private double longitude;
    private String description;
    private ReportType reportType;
    private ReportEnvironment environment;
    private List<ReportObjectRequest> objects;
}
