package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for `POST /api/reports`.")
public class CreateReportRequest {

    @Schema(description = "Id of the user creating the report (the authenticated caller).", example = "42")
    private Long userId;

    @Schema(description = "WGS84 latitude.", example = "41.085", minimum = "-90", maximum = "90",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private double latitude;

    @Schema(description = "WGS84 longitude.", example = "29.045", minimum = "-180", maximum = "180",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private double longitude;

    @Schema(description = "Free-text description of the issue. Hard-capped at 1000 characters.",
            example = "Construction blocks the ramp on the south side of the bridge.",
            maxLength = 1000)
    private String description;

    @Schema(description = "High-level report type — obstacle vs. positive feature.", example = "OBSTACLE")
    private ReportType reportType;

    @Schema(description = "Where the issue is located.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "Per-object details (object type, issues, measurements).")
    private List<ReportObjectRequest> objects;
}
