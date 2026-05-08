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
    private ReportType reportType;
    private ReportEnvironment environment;
    private ReportStatus status;
    private int agrees;
    private int disagrees;
    private Instant publishDate;
    private Instant fixedAt;
    private List<String> mediaUrls;
    private VoteType userVote;
    private FixRequestResponse activeFixRequest;
    private List<ReportObjectResponse> objects;
    private Double entryLatitude;
    private Double entryLongitude;
    private Double exitLatitude;
    private Double exitLongitude;
    private Long lastEditedByUserId;

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null, Collections.emptyList(), null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote) {
        return fromEntity(report, userVote, Collections.emptyList(), null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote, List<ReportObjectResponse> objectResponses) {
        return fromEntity(report, userVote, objectResponses, null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote,
                                            List<ReportObjectResponse> objectResponses,
                                            FixRequestResponse activeFixRequest) {
        ReportResponse r = new ReportResponse();
        r.setReportId(report.getReportId());
        r.setUserId(report.getCreatedBy().getId());
        if (report.getLocation() != null) {
            r.setLatitude(report.getLocation().getY());
            r.setLongitude(report.getLocation().getX());
        }
        r.setDescription(report.getDescription());
        r.setReportType(report.getReportType());
        r.setEnvironment(report.getEnvironment());
        r.setStatus(report.getStatus());
        r.setAgrees(report.getAgrees());
        r.setDisagrees(report.getDisagrees());
        r.setPublishDate(report.getPublishDate());
        r.setFixedAt(report.getFixedAt());
        r.setMediaUrls(report.getMediaList().stream().map(Media::getFilePath).toList());
        r.setUserVote(userVote);
        r.setActiveFixRequest(activeFixRequest);
        r.setObjects(objectResponses != null ? objectResponses : Collections.emptyList());

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
}
