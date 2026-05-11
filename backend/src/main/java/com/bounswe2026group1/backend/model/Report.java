package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser createdBy;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private int agrees = 0;
    private int disagrees = 0;

    private Instant publishDate;

    // Set when the report transitions to FIXED; consumed by the scheduled deletion job.
    private Instant fixedAt;

    // Nullable — only relevant for FEATURE reports used in wheelchair routing
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_edited_by_id")
    private RegisteredUser lastEditedBy;

    // JSON array of edit records — appended on every edit, never overwritten
    @Column(name = "edit_history", columnDefinition = "TEXT")
    private String editHistory;

    // Human-readable place name reverse-geocoded once at create time.
    // Nullable: an outage during create leaves this blank and the frontend
    // falls back to the rounded coordinates.
    @Column(name = "location_label", length = 200)
    private String locationLabel;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Media> mediaList = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportVerification> verifications = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReportObject> objects = new ArrayList<>();

    // Cascade so deleting a Report drops its fix requests (and their votes/media).
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FixRequest> fixRequests = new ArrayList<>();

    public Report(RegisteredUser createdBy, Point location, String description,
                  ReportType reportType, ReportEnvironment environment) {
        this.createdBy = createdBy;
        this.location = location;
        this.description = description;
        this.reportType = reportType;
        this.environment = environment;
        this.status = ReportStatus.PENDING;
        this.publishDate = Instant.now();
    }

    public void incrementAgrees()    { this.agrees++; }
    public void decrementAgrees()    { if (this.agrees > 0) this.agrees--; }
    public void incrementDisagrees() { this.disagrees++; }
    public void decrementDisagrees() { if (this.disagrees > 0) this.disagrees--; }
}
