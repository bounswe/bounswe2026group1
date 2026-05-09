package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Soft validation warning against a single measurement field on a report object.")
public class MeasurementWarning {

    @Schema(description = "Name of the measurement field that triggered the warning.",
            example = "slopePercent")
    private String field;

    @Schema(description = "Human-readable warning message.",
            example = "Slope above 12% is unusual for a public ramp; double-check the value.")
    private String message;
}
