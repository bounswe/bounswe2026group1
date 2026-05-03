package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private Long reportId;
    private Long userId;
    private double latitude;
    private double longitude;
    private String description;
    private Long categoryId;
    private String categoryName;
    private ReportType categoryType;
    private ReportEnvironment environment;
    private ReportStatus status;
    private int agrees;
    private int disagrees;
    private Instant publishDate;
    private List<String> mediaUrls;
    private VoteType userVote;
    private String measurements;
    private List<MeasurementWarning> warnings;
    private Double entryLatitude;
    private Double entryLongitude;
    private Double exitLatitude;
    private Double exitLongitude;
    private Long lastEditedByUserId;

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null, Collections.emptyList());
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote) {
        return fromEntity(report, userVote, Collections.emptyList());
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote, List<MeasurementWarning> warnings) {
        ReportResponse r = new ReportResponse();
        r.setReportId(report.getReportId());
        r.setUserId(report.getCreatedBy().getId());
        r.setLatitude(report.getLocation().getLatitude());
        r.setLongitude(report.getLocation().getLongitude());
        r.setDescription(report.getDescription());

        ReportCategory category = report.getCategory();
        r.setCategoryId(category.getId());
        r.setCategoryName(category.getName());
        // type may be null on child nodes — traverse up to root to find it
        r.setCategoryType(resolveType(category));

        r.setEnvironment(report.getEnvironment());
        r.setStatus(report.getStatus());
        r.setAgrees(report.getAgrees());
        r.setDisagrees(report.getDisagrees());
        r.setPublishDate(report.getPublishDate());
        r.setMediaUrls(report.getMediaList().stream().map(Media::getFilePath).toList());
        r.setUserVote(userVote);
        r.setMeasurements(report.getMeasurements());
        r.setWarnings(warnings != null ? warnings : Collections.emptyList());

        if (report.getEntryPoint() != null) {
            r.setEntryLatitude(report.getEntryPoint().getLatitude());
            r.setEntryLongitude(report.getEntryPoint().getLongitude());
        }
        if (report.getExitPoint() != null) {
            r.setExitLatitude(report.getExitPoint().getLatitude());
            r.setExitLongitude(report.getExitPoint().getLongitude());
        }
        if (report.getLastEditedBy() != null) {
            r.setLastEditedByUserId(report.getLastEditedBy().getId());
        }
        return r;
    }

    private static ReportType resolveType(ReportCategory category) {
        ReportCategory current = category;
        while (current != null) {
            if (current.getType() != null) return current.getType();
            current = current.getParent();
        }
        return null;
    }
}
