package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import lombok.Data;

@Data
public class AdminValidationResponse {
    private Long id;
    private Long userId;
    private String userName;
    private Long reportId;
    private VoteType voteType;

    public static AdminValidationResponse fromEntity(ReportVerification rv) {
        AdminValidationResponse r = new AdminValidationResponse();
        r.setId(rv.getId());
        r.setUserId(rv.getUser().getId());
        r.setUserName(rv.getUser().getName());
        r.setReportId(rv.getReport().getReportId());
        r.setVoteType(rv.getVoteType());
        return r;
    }
}
