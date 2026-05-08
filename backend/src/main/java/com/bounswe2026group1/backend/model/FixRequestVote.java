package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fix_request_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "fix_request_id"}))
public class FixRequestVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fix_request_id", nullable = false)
    private FixRequest fixRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteType voteType;

    public FixRequestVote() {}

    public FixRequestVote(RegisteredUser user, FixRequest fixRequest, VoteType voteType) {
        this.user = user;
        this.fixRequest = fixRequest;
        this.voteType = voteType;
    }

    public Long getId() { return id; }
    public RegisteredUser getUser() { return user; }
    public FixRequest getFixRequest() { return fixRequest; }
    public VoteType getVoteType() { return voteType; }

    public void setVoteType(VoteType voteType) { this.voteType = voteType; }
}
