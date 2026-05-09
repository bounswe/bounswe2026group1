package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An accessibility report along with vote counts, media, and the caller's vote (if any).")
public class ReportResponse {

    @Schema(description = "Report id.", example = "1024")
    private Long reportId;

    @Schema(description = "Id of the user who submitted the report.", example = "42")
    private Long userId;

    @Schema(description = "WGS84 latitude.", example = "41.085", minimum = "-90", maximum = "90")
    private double latitude;

    @Schema(description = "WGS84 longitude.", example = "29.045", minimum = "-180", maximum = "180")
    private double longitude;

    @Schema(description = "Free-text description (≤ 1000 chars).",
            example = "Construction blocks the ramp on the south side of the bridge.",
            maxLength = 1000)
    private String description;

    @Schema(description = "High-level report type.", example = "OBSTACLE")
    private ReportType reportType;

    @Schema(description = "Where the issue is located.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "Lifecycle status of the report.", example = "PENDING")
    private ReportStatus status;

    @Schema(description = "Number of users who voted to verify this report.", example = "7")
    private int agrees;

    @Schema(description = "Number of users who voted that this report is not accurate.", example = "1")
    private int disagrees;

    @Schema(description = "When the report was first submitted (UTC).",
            example = "2026-05-04T13:22:11Z")
    private Instant publishDate;

    @Schema(description = "When the report was marked FIXED, if ever (UTC).",
            example = "2026-05-08T09:10:00Z", nullable = true)
    private Instant fixedAt;

    @Schema(description = "Public S3 URLs for any photos or videos attached to the report.",
            example = "[\"https://mapcess-prod.s3.amazonaws.com/reports/1024-1.jpg\"]")
    private List<String> mediaUrls;

    @Schema(description = "How the authenticated caller voted on this report; null for anonymous callers or no vote.",
            nullable = true, example = "AGREE")
    private VoteType userVote;

    @Schema(description = "The currently OPEN fix request for this report, if any.", nullable = true)
    private FixRequestResponse activeFixRequest;

    @Schema(description = "Objects (door, ramp, elevator…) that the report refers to.")
    private List<ReportObjectResponse> objects;

    @Schema(description = "Latitude of the obstacle's entry point, when applicable.",
            example = "41.0851", nullable = true)
    private Double entryLatitude;

    @Schema(description = "Longitude of the obstacle's entry point, when applicable.",
            example = "29.0449", nullable = true)
    private Double entryLongitude;

    @Schema(description = "Latitude of the obstacle's exit point, when applicable.",
            example = "41.0853", nullable = true)
    private Double exitLatitude;

    @Schema(description = "Longitude of the obstacle's exit point, when applicable.",
            example = "29.0451", nullable = true)
    private Double exitLongitude;

    @Schema(description = "Id of the user who last edited the report (or null if never edited since creation).",
            example = "42", nullable = true)
    private Long lastEditedByUserId;

    @Schema(description = "Highest-tier badge held by the report author — surfaced as a chip next " +
            "to their name. Null when the author has no badges yet.",
            nullable = true)
    private Badge authorTopBadge;

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
