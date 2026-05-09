package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Payload for `PATCH /api/admin/reports/{id}/status` — force a report into a chosen status.")
public class AdminReportStatusRequest {

    @Schema(description = "New status to apply.", example = "VERIFIED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private ReportStatus status;
}
