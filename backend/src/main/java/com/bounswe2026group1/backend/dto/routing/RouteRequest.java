package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {

    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;
    private TravelMode mode;
}
