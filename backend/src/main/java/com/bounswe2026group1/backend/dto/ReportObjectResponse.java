package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.ReportObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Server-side view of a `ReportObjectRequest` after measurement validation has run.")
public class ReportObjectResponse {

    @Schema(description = "Stable id for this object, used to target measurement contributions.",
            example = "42")
    private Long id;

    @Schema(description = "Type of object.", example = "RAMP")
    private ObjectType objectType;

    @Schema(description = "Issues affecting this object.", example = "[\"BROKEN\"]")
    private Set<IssueType> issues;

    @Schema(description = "Object-specific measurements (JSON-encoded string).",
            example = "{\"slopePercent\": 14}")
    private String measurements;

    @Schema(description = "Soft validation warnings raised against the measurements (e.g. value out of expected range).")
    private List<MeasurementWarning> warnings;

    public static ReportObjectResponse fromEntity(ReportObject obj, List<MeasurementWarning> warnings) {
        return new ReportObjectResponse(
                obj.getId(),
                obj.getObjectType(),
                obj.getIssues(),
                obj.getMeasurements(),
                warnings != null ? warnings : List.of()
        );
    }
}
