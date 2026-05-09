package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.FixRequest;
import com.bounswe2026group1.backend.model.FixRequestMedia;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.VoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A 'this issue is fixed' submission against a report, plus its current vote tally and the caller's vote.")
public class FixRequestResponse {

    @Schema(description = "Fix request id.", example = "27")
    private Long id;

    @Schema(description = "Id of the parent report this fix request is for.", example = "1024")
    private Long reportId;

    @Schema(description = "Id of the user who submitted the fix request.", example = "42")
    private Long submittedByUserId;

    @Schema(description = "Display name of the submitter.", example = "Alice Example")
    private String submittedByName;

    @Schema(description = "Free-text justification of how/why the issue is fixed.",
            example = "Construction barrier removed; ramp is clear again.", maxLength = 1000)
    private String description;

    @Schema(description = "Lifecycle state of the fix request.", example = "OPEN")
    private FixRequestState state;

    @Schema(description = "Number of users who agree the issue is fixed.", example = "3")
    private int agrees;

    @Schema(description = "Number of users who disagree.", example = "0")
    private int disagrees;

    @Schema(description = "When the fix request was submitted (UTC).",
            example = "2026-05-08T08:00:00Z")
    private Instant createdAt;

    @Schema(description = "When the fix request was resolved — accepted, rejected, or expired (UTC). " +
            "Null while still OPEN.", example = "2026-05-09T08:00:00Z", nullable = true)
    private Instant resolvedAt;

    @Schema(description = "Public S3 URLs of evidence media attached to the fix request.")
    private List<String> mediaUrls;

    @Schema(description = "How the authenticated caller voted on this fix request; null for anonymous callers or no vote.",
            example = "AGREE", nullable = true)
    private VoteType userVote;

    public static FixRequestResponse fromEntity(FixRequest fixRequest) {
        return fromEntity(fixRequest, null);
    }

    public static FixRequestResponse fromEntity(FixRequest fixRequest, VoteType userVote) {
        FixRequestResponse response = new FixRequestResponse();
        response.setId(fixRequest.getId());
        response.setReportId(fixRequest.getReport().getReportId());
        response.setSubmittedByUserId(fixRequest.getSubmittedBy().getId());
        response.setSubmittedByName(fixRequest.getSubmittedBy().getName());
        response.setDescription(fixRequest.getDescription());
        response.setState(fixRequest.getState());
        response.setAgrees(fixRequest.getAgrees());
        response.setDisagrees(fixRequest.getDisagrees());
        response.setCreatedAt(fixRequest.getCreatedAt());
        response.setResolvedAt(fixRequest.getResolvedAt());
        response.setMediaUrls(
                fixRequest.getMediaList().stream()
                        .map(FixRequestMedia::getFilePath)
                        .toList()
        );
        response.setUserVote(userVote);
        return response;
    }
}
