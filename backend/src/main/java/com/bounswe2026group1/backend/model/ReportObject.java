package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "report_objects")
@Getter
@Setter
@NoArgsConstructor
public class ReportObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private ObjectType objectType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "report_object_issues",
            joinColumns = @JoinColumn(name = "report_object_id"))
    @Column(name = "issue_type")
    @Enumerated(EnumType.STRING)
    private Set<IssueType> issues = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String measurements;

    public ReportObject(Report report, ObjectType objectType, Set<IssueType> issues, String measurements) {
        this.report = report;
        this.objectType = objectType;
        this.issues = issues != null ? issues : new HashSet<>();
        this.measurements = measurements;
    }
}
