package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.model.VoteType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private Tag tag;
    private ReportStatus status;
    private int agrees;
    private int disagrees;
    private Instant publishDate;
    private Instant fixedAt;
    private List<String> mediaUrls;
    private VoteType userVote;
    private FixRequestResponse activeFixRequest;

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null, null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote) {
        return fromEntity(report, userVote, null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote, FixRequestResponse activeFixRequest) {
        ReportResponse response = new ReportResponse();
        response.setReportId(report.getReportId());
        response.setUserId(report.getCreatedBy().getId());
        response.setLatitude(report.getLocation().getLatitude());
        response.setLongitude(report.getLocation().getLongitude());
        response.setDescription(report.getDescription());
        response.setTag(report.getTag());
        response.setStatus(report.getStatus());
        response.setAgrees(report.getAgrees());
        response.setDisagrees(report.getDisagrees());
        response.setPublishDate(report.getPublishDate());
        response.setFixedAt(report.getFixedAt());
        response.setMediaUrls(
                report.getMediaList().stream()
                        .map(Media::getFilePath)
                        .toList()
        );
        response.setUserVote(userVote);
        response.setActiveFixRequest(activeFixRequest);
        return response;
    }
}
