package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update for `PUT /api/reports/{id}` — only the fields you send are applied.")
public class UpdateReportRequest {

    @Schema(description = "New description (hard-capped at 1000 characters).",
            example = "Construction blocks the ramp on the south side of the bridge.",
            maxLength = 1000)
    private String description;

    @Schema(description = "New environment.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "New WGS84 latitude.", example = "41.086",
            minimum = "-90", maximum = "90", nullable = true)
    private Double latitude;

    @Schema(description = "New WGS84 longitude.", example = "29.044",
            minimum = "-180", maximum = "180", nullable = true)
    private Double longitude;

    @Schema(description = "Replacement list of attached objects. " +
            "Sending `null` leaves the existing objects untouched; sending an empty list clears them.")
    private List<ReportObjectRequest> objects;

    @Schema(description = "Ids of existing media records to detach from this report.",
            example = "[12, 13]")
    private List<Long> mediaIdsToRemove;
}
