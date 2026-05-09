package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.TravelMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Compact summary of a previously-planned route, used to populate the user's route history.")
public class RouteSummaryDTO {

    @Schema(description = "Route id.", example = "501")
    private Long id;

    @Schema(description = "Start coordinates.")
    private Location startLocation;

    @Schema(description = "End coordinates.")
    private Location endLocation;

    @Schema(description = "Total route length in meters.", example = "1820")
    private int distance;

    @Schema(description = "Estimated travel time in seconds.", example = "1320")
    private int duration;

    @Schema(description = "Travel mode used for this route.", example = "WALKING")
    private TravelMode travelMode;

    @Schema(description = "Id of the user who planned the route.", example = "42", nullable = true)
    private Long createdById;

    public static RouteSummaryDTO from(Route r) {
        return RouteSummaryDTO.builder()
                .id(r.getId())
                .startLocation(r.getStartLocation())
                .endLocation(r.getEndLocation())
                .distance(r.getDistance())
                .duration(r.getDuration())
                .travelMode(r.getTravelMode())
                .createdById(r.getCreatedBy() != null ? r.getCreatedBy().getId() : null)
                .build();
    }
}
