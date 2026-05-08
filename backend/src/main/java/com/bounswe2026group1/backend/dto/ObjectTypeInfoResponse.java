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
public class ObjectTypeInfoResponse {
    private ObjectType objectType;
    private String description;
    private List<IssueInfoResponse> issues;
    private Object measurementSchema;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueInfoResponse {
        private IssueType issueType;
        private String description;
    }
}
