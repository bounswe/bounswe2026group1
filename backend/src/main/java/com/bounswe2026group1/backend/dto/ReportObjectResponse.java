package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.ReportObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportObjectResponse {
    private ObjectType objectType;
    private Set<IssueType> issues;
    private String measurements;
    private List<MeasurementWarning> warnings;

    public static ReportObjectResponse fromEntity(ReportObject obj, List<MeasurementWarning> warnings) {
        return new ReportObjectResponse(
                obj.getObjectType(),
                obj.getIssues(),
                obj.getMeasurements(),
                warnings != null ? warnings : List.of()
        );
    }
}
