package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Fill-in contribution: a partial set of measurement keys to merge into a "
        + "report object's existing measurements. Only keys not already populated may be set; "
        + "attempting to overwrite an existing non-empty value yields 409 Conflict.")
public class MeasurementContributionRequest {

    @Schema(description = "Measurement keys → values to add. Numeric values should be sent as "
            + "JSON numbers so validation against the per-object schema can run.",
            example = "{\"width_cm\": 95, \"slope_percent\": 7}")
    private Map<String, Object> values;
}
