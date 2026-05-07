package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectTypeServiceTest {

    private ObjectTypeService objectTypeService;

    @BeforeEach
    void setUp() {
        objectTypeService = new ObjectTypeService();
    }

    @Test
    void getAll_returnsFiveObjectTypes() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();

        assertEquals(5, result.size());
    }

    @Test
    void getAll_allObjectTypesPresent() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();
        List<ObjectType> returnedTypes = result.stream().map(ObjectTypeInfoResponse::getObjectType).toList();

        assertTrue(returnedTypes.contains(ObjectType.RAMP));
        assertTrue(returnedTypes.contains(ObjectType.ELEVATOR));
        assertTrue(returnedTypes.contains(ObjectType.SIDEWALK));
        assertTrue(returnedTypes.contains(ObjectType.DOOR));
        assertTrue(returnedTypes.contains(ObjectType.STAIR));
    }

    @Test
    void getAll_eachTypeHasNonEmptyDescription() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();

        for (ObjectTypeInfoResponse r : result) {
            assertNotNull(r.getDescription(), r.getObjectType() + " has null description");
            assertFalse(r.getDescription().isBlank(), r.getObjectType() + " has blank description");
        }
    }

    @Test
    void getAll_eachTypeHasAtLeastOneIssue() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();

        for (ObjectTypeInfoResponse r : result) {
            assertFalse(r.getIssues().isEmpty(),
                    r.getObjectType() + " should have at least one valid issue");
        }
    }

    @Test
    void getAll_eachIssueHasNonEmptyDescription() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();

        for (ObjectTypeInfoResponse r : result) {
            for (ObjectTypeInfoResponse.IssueInfoResponse issue : r.getIssues()) {
                assertNotNull(issue.getDescription());
                assertFalse(issue.getDescription().isBlank(),
                        issue.getIssueType() + " has blank description");
            }
        }
    }

    @Test
    void getAll_rampIssues_containsExpectedTypes() {
        ObjectTypeInfoResponse ramp = objectTypeService.getAll().stream()
                .filter(r -> r.getObjectType() == ObjectType.RAMP)
                .findFirst().orElseThrow();

        List<IssueType> rampIssues = ramp.getIssues().stream()
                .map(ObjectTypeInfoResponse.IssueInfoResponse::getIssueType)
                .toList();

        assertTrue(rampIssues.contains(IssueType.TOO_STEEP));
        assertTrue(rampIssues.contains(IssueType.TOO_NARROW));
        assertTrue(rampIssues.contains(IssueType.MISSING_HANDRAIL));
        assertTrue(rampIssues.contains(IssueType.MISSING));
        // ELEVATOR-only issue must NOT appear under RAMP
        assertFalse(rampIssues.contains(IssueType.OUT_OF_SERVICE));
    }

    @Test
    void getAll_elevatorIssues_doNotContainRampSpecificIssues() {
        ObjectTypeInfoResponse elevator = objectTypeService.getAll().stream()
                .filter(r -> r.getObjectType() == ObjectType.ELEVATOR)
                .findFirst().orElseThrow();

        List<IssueType> issues = elevator.getIssues().stream()
                .map(ObjectTypeInfoResponse.IssueInfoResponse::getIssueType)
                .toList();

        assertTrue(issues.contains(IssueType.OUT_OF_SERVICE));
        assertFalse(issues.contains(IssueType.TOO_STEEP));
        assertFalse(issues.contains(IssueType.RISER_TOO_HIGH));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_rampHasMeasurementSchema() {
        ObjectTypeInfoResponse ramp = objectTypeService.getAll().stream()
                .filter(r -> r.getObjectType() == ObjectType.RAMP)
                .findFirst().orElseThrow();

        assertNotNull(ramp.getMeasurementSchema(), "RAMP should have a measurement schema");
        Map<String, Object> schema = (Map<String, Object>) ramp.getMeasurementSchema();
        assertTrue(schema.containsKey("slope_percent"));
        assertTrue(schema.containsKey("width_cm"));
    }

    @Test
    void getAll_missingIssue_validForAllObjectTypes() {
        List<ObjectTypeInfoResponse> result = objectTypeService.getAll();

        for (ObjectTypeInfoResponse r : result) {
            boolean hasMissing = r.getIssues().stream()
                    .anyMatch(i -> i.getIssueType() == IssueType.MISSING);
            assertTrue(hasMissing, r.getObjectType() + " should include the MISSING issue");
        }
    }
}
