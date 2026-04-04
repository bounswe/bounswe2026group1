package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("RAMP_REPORT")
public class RampReport extends Report {

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude",  column = @Column(name = "entry_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "entry_longitude"))
    })
    private Location entryPoint;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude",  column = @Column(name = "exit_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "exit_longitude"))
    })
    private Location exitPoint;

    public RampReport(RegisteredUser createdBy, Location location, String description,
                      Location entryPoint, Location exitPoint) {
        super(createdBy, location, description, Tag.RAMP);
        this.entryPoint = entryPoint;
        this.exitPoint = exitPoint;
    }
}