package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial profile update — only the fields you send are applied.")
public class UpdateProfileRequest {

    @Schema(description = "Display name.", example = "Alice Example", minLength = 2, maxLength = 50)
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @Schema(description = "Free-text bio shown on the user's profile.",
            example = "Wheelchair user mapping ramps in Beşiktaş.", maxLength = 500)
    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;
}
