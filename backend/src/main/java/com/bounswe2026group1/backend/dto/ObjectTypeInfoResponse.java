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
@Schema(description = "Catalogue entry describing one report object type, its valid issues, and its measurement schema.")
public class ObjectTypeInfoResponse {

    @Schema(description = "Object type identifier.", example = "RAMP")
    private ObjectType objectType;

    @Schema(description = "Human-readable description rendered next to the object type in the form.",
            example = "A wheelchair-accessible ramp.")
    private String description;

    @Schema(description = "Issues that can be reported against this object type.")
    private List<IssueInfoResponse> issues;

    @Schema(description = "JSON Schema describing the `measurements` blob expected for this object type. " +
            "Object shape; treat as opaque on the client and pass through to the validator.",
            example = "{\"type\":\"object\",\"properties\":{\"slopePercent\":{\"type\":\"number\"}}}")
    private Object measurementSchema;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Issue catalogue entry — the issue type and its descriptive label.")
    public static class IssueInfoResponse {

        @Schema(description = "Issue type identifier.", example = "BROKEN")
        private IssueType issueType;

        @Schema(description = "Human-readable description shown alongside the issue type.",
                example = "The object is broken or out of service.")
        private String description;
    }
}
