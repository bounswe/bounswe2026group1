package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.model.ReportType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ReportCategoryResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Long id;
    private final String name;
    private final ReportType type;
    private final List<String> affectedProfiles;
    private final Object measurementSchema;
    private final List<ReportCategoryResponse> children;

    public ReportCategoryResponse(ReportCategory category, List<ReportCategoryResponse> children) {
        this.id = category.getId();
        this.name = category.getName();
        this.type = category.getType();
        this.affectedProfiles = parseProfiles(category.getAffectedProfiles());
        this.measurementSchema = parseSchema(category.getMeasurementSchema());
        this.children = children;
    }

    private static List<String> parseProfiles(String json) {
        if (json == null) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Object parseSchema(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
