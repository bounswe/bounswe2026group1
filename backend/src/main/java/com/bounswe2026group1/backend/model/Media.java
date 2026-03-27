package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
@Entity
@Table(name = "media")
public class Media {
    @Id                 // PK of the table
    @GeneratedValue(strategy = GenerationType.IDENTITY)         // Auto-incremented
    private Long mediaId;

    // FK of the table from report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    // URL that is returned from AWS or Firebase
    @Column(nullable = false)
    private String filePath;

    public Media() {}

    public Media(Report report, String fileUrl) {
        this.report = report;
        this.filePath = fileUrl;
    }

    // Dummy show method for backend
    public void show() {
        System.out.println("Medya is showed. File path is: " + this.filePath);
    }

    // Getters and setters
    public Long getId() { return mediaId; }
    public Report getReport() { return report; }
    public String getFilePath() { return filePath; }

    // Setters
    public void setReport(Report report) { this.report = report; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
