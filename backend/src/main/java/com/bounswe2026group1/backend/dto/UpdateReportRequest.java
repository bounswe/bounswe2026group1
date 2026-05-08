package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportRequest {
    private String description;
    private ReportEnvironment environment;
    private Double latitude;
    private Double longitude;
    private List<ReportObjectRequest> objects;
    private List<Long> mediaIdsToRemove;
}
