package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fix_requests",
        indexes = {
                @Index(name = "idx_fix_request_report", columnList = "report_id"),
                @Index(name = "idx_fix_request_state", columnList = "state")
        })
public class FixRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private RegisteredUser submittedBy;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FixRequestState state;

    private int agrees = 0;
    private int disagrees = 0;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @OneToMany(mappedBy = "fixRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FixRequestMedia> mediaList = new ArrayList<>();

    @OneToMany(mappedBy = "fixRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FixRequestVote> votes = new ArrayList<>();

    public FixRequest() {}

    public FixRequest(Report report, RegisteredUser submittedBy, String description) {
        this.report = report;
        this.submittedBy = submittedBy;
        this.description = description;
        this.state = FixRequestState.OPEN;
        this.createdAt = Instant.now();
    }

    public void incrementAgrees() { this.agrees++; }
    public void decrementAgrees() { if (this.agrees > 0) this.agrees--; }
    public void incrementDisagrees() { this.disagrees++; }
    public void decrementDisagrees() { if (this.disagrees > 0) this.disagrees--; }

    public Long getId() { return id; }
    public Report getReport() { return report; }
    public RegisteredUser getSubmittedBy() { return submittedBy; }
    public String getDescription() { return description; }
    public FixRequestState getState() { return state; }
    public int getAgrees() { return agrees; }
    public int getDisagrees() { return disagrees; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public List<FixRequestMedia> getMediaList() { return mediaList; }
    public List<FixRequestVote> getVotes() { return votes; }

    public void setState(FixRequestState state) { this.state = state; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setDescription(String description) { this.description = description; }
}
