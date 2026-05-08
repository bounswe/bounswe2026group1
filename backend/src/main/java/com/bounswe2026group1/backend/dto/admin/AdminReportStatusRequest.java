package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminReportStatusRequest {
    @NotNull
    private ReportStatus status;
}
