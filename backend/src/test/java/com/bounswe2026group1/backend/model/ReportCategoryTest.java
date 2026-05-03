package com.bounswe2026group1.backend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReportCategoryTest {

    @Test
    void getEffectiveMeasurementSchema_ownSchemaPresent_returnsOwnSchema() {
        ReportCategory category = new ReportCategory();
        category.setMeasurementSchema("{\"width_cm\":{\"min\":0,\"max\":500}}");

        assertEquals("{\"width_cm\":{\"min\":0,\"max\":500}}", category.getEffectiveMeasurementSchema());
    }

    @Test
    void getEffectiveMeasurementSchema_noSchemaNoParent_returnsNull() {
        ReportCategory category = new ReportCategory();

        assertNull(category.getEffectiveMeasurementSchema());
    }

    @Test
    void getEffectiveMeasurementSchema_noOwnSchemaWithParent_inheritsParentSchema() {
        ReportCategory parent = new ReportCategory();
        parent.setMeasurementSchema("{\"slope_percent\":{\"min\":0,\"max\":100}}");

        ReportCategory child = new ReportCategory();
        child.setParent(parent);

        assertEquals("{\"slope_percent\":{\"min\":0,\"max\":100}}", child.getEffectiveMeasurementSchema());
    }

    @Test
    void getEffectiveMeasurementSchema_ownSchemaOverridesParent() {
        ReportCategory parent = new ReportCategory();
        parent.setMeasurementSchema("{\"width_cm\":{\"min\":0,\"max\":500}}");

        ReportCategory child = new ReportCategory();
        child.setParent(parent);
        child.setMeasurementSchema("{\"height_cm\":{\"min\":0,\"max\":30}}");

        assertEquals("{\"height_cm\":{\"min\":0,\"max\":30}}", child.getEffectiveMeasurementSchema());
    }

    @Test
    void getEffectiveMeasurementSchema_parentHasNoSchema_returnsNull() {
        ReportCategory parent = new ReportCategory();

        ReportCategory child = new ReportCategory();
        child.setParent(parent);

        assertNull(child.getEffectiveMeasurementSchema());
    }
}
