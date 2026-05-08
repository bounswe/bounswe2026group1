package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.FixRequest;
import com.bounswe2026group1.backend.model.FixRequestMedia;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.VoteType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FixRequestResponse {
    private Long id;
    private Long reportId;
    private Long submittedByUserId;
    private String submittedByName;
    private String description;
    private FixRequestState state;
    private int agrees;
    private int disagrees;
    private Instant createdAt;
    private Instant resolvedAt;
    private List<String> mediaUrls;
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
