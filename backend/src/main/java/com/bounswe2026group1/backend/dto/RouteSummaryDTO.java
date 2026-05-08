package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Route;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteSummaryDTO {

    private Long id;
    private Location startLocation;
    private Location endLocation;
    private int distance;
    private int duration;
    private TravelMode travelMode;
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
