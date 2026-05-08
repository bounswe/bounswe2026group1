package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fix_request_media")
public class FixRequestMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fix_request_id", nullable = false)
    private FixRequest fixRequest;

    @Column(nullable = false)
    private String filePath;

    public FixRequestMedia() {}

    public FixRequestMedia(FixRequest fixRequest, String filePath) {
        this.fixRequest = fixRequest;
        this.filePath = filePath;
    }

    public Long getId() { return id; }
    public FixRequest getFixRequest() { return fixRequest; }
    public String getFilePath() { return filePath; }

    public void setFixRequest(FixRequest fixRequest) { this.fixRequest = fixRequest; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
