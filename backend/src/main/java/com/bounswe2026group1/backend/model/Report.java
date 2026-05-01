package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports",
        indexes = @Index(name = "idx_report_location", columnList = "latitude, longitude"))
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser createdBy;

    @Embedded
    private Location location;

    @Column(nullable = false, length = 1000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ReportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private int agrees = 0;
    private int disagrees = 0;

    private LocalDateTime publishDate;

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

    // JSON object: { "slope_percent": 12, "width_cm": 75 } — null if not submitted
    @Column(columnDefinition = "TEXT")
    private String measurements;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_edited_by_id")
    private RegisteredUser lastEditedBy;

    // JSON array of edit records — appended on every edit, never overwritten
    @Column(name = "edit_history", columnDefinition = "TEXT")
    private String editHistory;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Media> mediaList = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportVerification> verifications = new ArrayList<>();

    public Report() {}

    public Report(RegisteredUser createdBy, Location location, String description,
                  ReportCategory category, ReportEnvironment environment) {
        this.createdBy = createdBy;
        this.location = location;
        this.description = description;
        this.category = category;
        this.environment = environment;
        this.status = ReportStatus.PENDING;
        this.publishDate = LocalDateTime.now();
    }

    public void incrementAgrees()   { this.agrees++; }
    public void decrementAgrees()   { if (this.agrees > 0) this.agrees--; }
    public void incrementDisagrees() { this.disagrees++; }
    public void decrementDisagrees() { if (this.disagrees > 0) this.disagrees--; }

    public Long getReportId()               { return reportId; }
    public RegisteredUser getCreatedBy()    { return createdBy; }
    public Location getLocation()           { return location; }
    public String getDescription()          { return description; }
    public ReportCategory getCategory()     { return category; }
    public ReportEnvironment getEnvironment() { return environment; }
    public ReportStatus getStatus()         { return status; }
    public int getAgrees()                  { return agrees; }
    public int getDisagrees()               { return disagrees; }
    public LocalDateTime getPublishDate()   { return publishDate; }
    public Location getEntryPoint()         { return entryPoint; }
    public Location getExitPoint()          { return exitPoint; }
    public String getMeasurements()         { return measurements; }
    public RegisteredUser getLastEditedBy() { return lastEditedBy; }
    public String getEditHistory()          { return editHistory; }
    public List<Media> getMediaList()       { return mediaList; }
    public List<Comment> getComments()      { return comments; }

    public void setStatus(ReportStatus status)          { this.status = status; }
    public void setDescription(String description)      { this.description = description; }
    public void setCategory(ReportCategory category)    { this.category = category; }
    public void setEnvironment(ReportEnvironment env)   { this.environment = env; }
    public void setEntryPoint(Location entryPoint)      { this.entryPoint = entryPoint; }
    public void setExitPoint(Location exitPoint)        { this.exitPoint = exitPoint; }
    public void setMeasurements(String measurements)    { this.measurements = measurements; }
    public void setLastEditedBy(RegisteredUser user)    { this.lastEditedBy = user; }
    public void setEditHistory(String editHistory)      { this.editHistory = editHistory; }
}
