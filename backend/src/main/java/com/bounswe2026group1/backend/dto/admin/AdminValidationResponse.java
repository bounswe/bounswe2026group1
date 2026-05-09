package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Admin moderation view of a single verification vote.")
public class AdminValidationResponse {

    @Schema(description = "Vote id.", example = "7001")
    private Long id;

    @Schema(description = "Id of the voting user.", example = "42")
    private Long userId;

    @Schema(description = "Display name of the voting user.", example = "Alice Example")
    private String userName;

    @Schema(description = "Id of the report being voted on.", example = "1024")
    private Long reportId;

    @Schema(description = "Direction of the vote.", example = "AGREE")
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
