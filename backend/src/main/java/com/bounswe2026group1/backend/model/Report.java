package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports",
        // Indexed for faster location based queries
        indexes = @Index(name = "idx_report_location", columnList = "latitude, longitude"))
// If it has RAMP tag it is also a RampReport table
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("CASE WHEN tag = 'RAMP' THEN 'RAMP_REPORT' ELSE 'DEFAULT_REPORT' END")
@DiscriminatorValue("DEFAULT_REPORT")
public class Report {
    @Id // PK of the table
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto incremented ID
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // A report cannot exist without a user.
    private RegisteredUser createdBy;

    @Embedded
    private Location location;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tag tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    // "agrees" and "disagrees" are mapped to integer columns. (Default)
    private int agrees = 0;
    private int disagrees = 0;

    private Instant publishDate;

    // Set when the report transitions to FIXED; consumed by the scheduled deletion job.
    private Instant fixedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Media> mediaList = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportVerification> verifications = new ArrayList<>();

    public Report() {
    }

    public Report(RegisteredUser createdBy, Location location, String description, Tag tag) {
        this.createdBy = createdBy;
        this.location = location;
        this.description = description;
        this.tag = tag;

        // Report status defaults to PENDING on creation
        this.status = ReportStatus.PENDING;
        this.publishDate = Instant.now();
    }

    public void incrementAgrees() { this.agrees++; }
    public void decrementAgrees() { if (this.agrees > 0) this.agrees--; }

    public void incrementDisagrees() { this.disagrees++; }
    public void decrementDisagrees() { if (this.disagrees > 0) this.disagrees--; }

    // GETTERS & SETTERS
    public Long getReportId() {
        return reportId;
    }

    public RegisteredUser getCreatedBy() {
        return createdBy;
    }

    public Location getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public Tag getTag() {
        return tag;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public int getAgrees() {
        return agrees;
    }

    public int getDisagrees() {
        return disagrees;
    }

    public Instant getPublishDate() {
        return publishDate;
    }

    public Instant getFixedAt() {
        return fixedAt;
    }

    public void setFixedAt(Instant fixedAt) {
        this.fixedAt = fixedAt;
    }

    public List<Media> getMediaList() {
        return mediaList;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

}
