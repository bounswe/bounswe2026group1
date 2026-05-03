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
    private Long categoryId;
    private ReportEnvironment environment;
    private Double latitude;
    private Double longitude;
    private String measurements;
    private List<Long> mediaIdsToRemove;
}
