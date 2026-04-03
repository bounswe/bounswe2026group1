package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "report_verifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "report_id"}))
public class ReportVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteType voteType;

    public ReportVerification() {}

    public ReportVerification(RegisteredUser user, Report report, VoteType voteType) {
        this.user = user;
        this.report = report;
        this.voteType = voteType;
    }

    public Long getId() { return id; }
    public RegisteredUser getUser() { return user; }
    public Report getReport() { return report; }
    public VoteType getVoteType() { return voteType; }
    public void setVoteType(VoteType voteType) { this.voteType = voteType; }
}
