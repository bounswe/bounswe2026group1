package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports")
// Positive reports are stored in seperate table but joined to all reports
// This inheritance
@Inheritance(strategy = InheritanceType.JOINED)
// This column is used to distinguish if it is normal report or ramp report
@DiscriminatorColumn(name = "report_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("DEFAULT_REPORT")
public class Report {
    @Id         // PK of the table
    @GeneratedValue(strategy = GenerationType.IDENTITY)         // Auto incremented ID
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)         // A report cannot exist without a user.
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

    private LocalDateTime publishDate;
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Media> mediaList = new ArrayList<>();

    public Report() {}

    public Report(RegisteredUser createdBy, Location location, String description, Tag tag) {
        this.createdBy = createdBy;
        this.location = location;
        this.description = description;
        this.tag = tag;

        // Report status defaults to PENDING on creation
        this.status = ReportStatus.PENDING;
        this.publishDate = LocalDateTime.now();
    }

    public void incrementAgrees() { this.agrees++; }
    public void incrementDisagrees() { this.disagrees++; }

    // GETTERS & SETTERS
    public Long getReportId() { return reportId; }
    public RegisteredUser getCreatedBy() { return createdBy; }
    public Location getLocation() { return location; }
    public String getDescription() { return description; }
    public Tag getTag() { return tag; }
    public ReportStatus getStatus() { return status; }
    public int getAgrees() { return agrees; }
    public int getDisagrees() { return disagrees; }
    public LocalDateTime getPublishDate() { return publishDate; }
    public List<Media> getMediaList() { return mediaList; }
    public List<Comment> getComments() { return comments; }

    public void setStatus(ReportStatus status) { this.status = status; }
    public void setDescription(String description) { this.description = description; }
    public void setTag(Tag tag) { this.tag = tag; }

}
