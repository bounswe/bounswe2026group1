package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.TravelMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Routing request for `POST /api/routes`.")
public class RouteRequest {

    @Schema(description = "WGS84 latitude of the route's start point.", example = "41.085",
            minimum = "-90", maximum = "90", requiredMode = Schema.RequiredMode.REQUIRED)
    private double startLat;

    @Schema(description = "WGS84 longitude of the route's start point.", example = "29.045",
            minimum = "-180", maximum = "180", requiredMode = Schema.RequiredMode.REQUIRED)
    private double startLon;

    @Schema(description = "WGS84 latitude of the route's end point.", example = "41.090",
            minimum = "-90", maximum = "90", requiredMode = Schema.RequiredMode.REQUIRED)
    private double endLat;

    @Schema(description = "WGS84 longitude of the route's end point.", example = "29.050",
            minimum = "-180", maximum = "180", requiredMode = Schema.RequiredMode.REQUIRED)
    private double endLon;

    @Schema(description = "Travel mode requested.", example = "WALKING",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TravelMode mode;
}
