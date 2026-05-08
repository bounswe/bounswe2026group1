package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse;
import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse.IssueInfoResponse;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ObjectTypeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<ObjectTypeInfoResponse> getAll() {
        return Arrays.stream(ObjectType.values())
                .map(this::toResponse)
                .toList();
    }

    private ObjectTypeInfoResponse toResponse(ObjectType objectType) {
        List<IssueInfoResponse> issues = Arrays.stream(IssueType.values())
                .filter(issue -> issue.isValidFor(objectType))
                .map(issue -> new IssueInfoResponse(issue, issue.getDescription()))
                .toList();

        Object schema = null;
        String schemaJson = MeasurementSchemas.getSchemaJson(objectType);
        if (schemaJson != null) {
            try {
                schema = MAPPER.readValue(schemaJson, Object.class);
            } catch (Exception ignored) {
                schema = schemaJson;
            }
        }

        return new ObjectTypeInfoResponse(objectType, objectType.getDescription(), issues, schema);
    }
}
