package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.MeasurementWarning;
import com.bounswe2026group1.backend.exception.MeasurementValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementValidatorTest {

    private MeasurementValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MeasurementValidator();
    }

    @Test
    void validate_returnsEmptyWhenEitherArgumentNull() {
        assertTrue(validator.validate(null, "{}").isEmpty());
        assertTrue(validator.validate("{}", null).isEmpty());
    }

    @Test
    void validate_throwsWhenBelowHardMin() {
        String measurements = "{\"slopePercent\": 5}";
        String schema = "{\"slopePercent\": {\"min\": 10.0}}";

        assertThrows(MeasurementValidationException.class,
                () -> validator.validate(measurements, schema));
    }

    @Test
    void validate_throwsWhenAboveHardMax() {
        String measurements = "{\"widthCm\": 300}";
        String schema = "{\"widthCm\": {\"max\": 200.0}}";

        assertThrows(MeasurementValidationException.class,
                () -> validator.validate(measurements, schema));
    }

    @Test
    void validate_returnsWarningsForAccessibleSoftLimits() {
        String measurements = "{\"riseCm\": 15}";
        String schema = "{\"riseCm\": {\"accessible_max\": 10.0, \"accessible_min\": 2.0}}";

        List<MeasurementWarning> warnings = validator.validate(measurements, schema);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().getMessage().toLowerCase().contains("accessible"));
    }

    @Test
    void validate_invalidJson_wrapsAsMeasurementValidationException() {
        assertThrows(MeasurementValidationException.class,
                () -> validator.validate("{not-json", "{}"));
    }
}
