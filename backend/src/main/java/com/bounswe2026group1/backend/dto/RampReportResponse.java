package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RampReportResponse {
    private Long reportId;
    private Long userId;
    private double latitude;
    private double longitude;
    private String description;
    private ReportStatus status;
    private Instant publishDate;
    private double entryLatitude;
    private double entryLongitude;
    private double exitLatitude;
    private double exitLongitude;

    public static RampReportResponse fromEntity(RampReport report) {
        RampReportResponse response = new RampReportResponse();
        response.setReportId(report.getReportId());
        response.setUserId(report.getCreatedBy().getId());
        response.setLatitude(report.getLocation().getLatitude());
        response.setLongitude(report.getLocation().getLongitude());
        response.setDescription(report.getDescription());
        response.setStatus(report.getStatus());
        response.setPublishDate(report.getPublishDate());
        response.setEntryLatitude(report.getEntryPoint().getLatitude());
        response.setEntryLongitude(report.getEntryPoint().getLongitude());
        response.setExitLatitude(report.getExitPoint().getLatitude());
        response.setExitLongitude(report.getExitPoint().getLongitude());
        return response;
    }
}
