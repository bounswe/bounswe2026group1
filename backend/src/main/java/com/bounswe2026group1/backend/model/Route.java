package com.bounswe2026group1.backend.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "routes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "start_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "start_longitude", nullable = false))
    })
    private Location startLocation;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "end_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "end_longitude", nullable = false))
    })
    private Location endLocation;

    @Column(nullable = false)
    private int distance;

    @Column(nullable = false)
    private int duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TravelMode travelMode;
}
