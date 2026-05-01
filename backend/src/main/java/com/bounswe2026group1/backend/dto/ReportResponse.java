package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.model.VoteType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime publishDate;
    private List<String> mediaUrls;
    private VoteType userVote;

    /** Populated when the report is linked to the redesigned category tree */
    private Long categoryId;
    private String categoryName;
    private ReportType reportType;
    private ReportEnvironment environment;

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null);
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote) {
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
        response.setMediaUrls(
                report.getMediaList().stream()
                        .map(Media::getFilePath)
                        .toList()
        );
        response.setUserVote(userVote);
        ReportCategory category = report.getCategory();
        if (category != null) {
            response.setCategoryId(category.getId());
            response.setCategoryName(category.getName());
            ReportType effectiveType = category.getType();
            if (effectiveType == null && category.getParent() != null) {
                effectiveType = category.getParent().getType();
            }
            response.setReportType(effectiveType);
        }
        response.setEnvironment(report.getEnvironment());
        return response;
    }
}
