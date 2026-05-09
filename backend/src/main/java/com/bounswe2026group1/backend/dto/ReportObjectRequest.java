package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One physical object referenced by a report (a door, ramp, elevator…) and the issues affecting it.")
public class ReportObjectRequest {

    @Schema(description = "Type of object the report is about.", example = "RAMP",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ObjectType objectType;

    @Schema(description = "Issues affecting this object. Valid values depend on `objectType`.",
            example = "[\"BROKEN\", \"NARROW_PASSAGE\"]")
    private List<IssueType> issues;

    @Schema(description = "Object-specific measurements (JSON-encoded string). " +
            "The expected schema is reported by `GET /api/object-types`.",
            example = "{\"slopePercent\": 14, \"widthCm\": 80}")
    private String measurements;
}
