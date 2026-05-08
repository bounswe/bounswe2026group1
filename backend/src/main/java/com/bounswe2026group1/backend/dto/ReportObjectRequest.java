package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportObjectRequest {
    private ObjectType objectType;
    private List<IssueType> issues;
    private String measurements;
}
