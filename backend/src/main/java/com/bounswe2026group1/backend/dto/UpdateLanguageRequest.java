package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Set the caller's UI language preference.")
public class UpdateLanguageRequest {

    @Schema(description = "Language code matching the Language enum.",
            example = "TR", allowableValues = {"EN", "TR"})
    @NotBlank(message = "Language must not be blank")
    private String language;
}
