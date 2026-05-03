package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report_categories")
@Getter
@Setter
@NoArgsConstructor
public class ReportCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ReportCategory parent;

    // Null on child nodes — inherited from top-level parent
    @Enumerated(EnumType.STRING)
    private ReportType type;

    // JSON array: e.g. ["WHEELCHAIR","STROLLER"] — defined on leaf nodes
    @Column(name = "affected_profiles", columnDefinition = "TEXT")
    private String affectedProfiles;

    // JSON object defining numeric measurement fields for this category — null if none
    @Column(name = "measurement_schema", columnDefinition = "TEXT")
    private String measurementSchema;

    // True for FEATURE categories that provide a physical path (entry/exit points)
    // and should be considered in wheelchair routing
    @Column(nullable = false)
    private boolean routingRelevant = false;

    // Defines which Overpass snap method to use when routingRelevant=true
    @Enumerated(EnumType.STRING)
    private SnapType snapType;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private LocationType locationType;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<ReportCategory> children = new ArrayList<>();

    // Returns own schema if set; falls back to parent's schema (one level up).
    // Leaf nodes under OBSTACLE parents inherit the parent's measurement schema.
    public String getEffectiveMeasurementSchema() {
        if (measurementSchema != null) return measurementSchema;
        if (parent != null) return parent.getMeasurementSchema();
        return null;
    }
}
