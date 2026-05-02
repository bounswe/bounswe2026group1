package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.MeasurementWarning;
import com.bounswe2026group1.backend.exception.MeasurementValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MeasurementValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Validates user-submitted measurements against the category's schema.
     * Hard limits (min/max) throw MeasurementValidationException.
     * Soft limits (accessible_min/accessible_max) return warnings.
     */
    public List<MeasurementWarning> validate(String measurementsJson, String schemaJson) {
        if (measurementsJson == null || schemaJson == null) {
            return List.of();
        }

        try {
            Map<String, Number> measurements = MAPPER.readValue(
                    measurementsJson, new TypeReference<>() {});
            Map<String, Map<String, Double>> schema = MAPPER.readValue(
                    schemaJson, new TypeReference<>() {});

            List<MeasurementWarning> warnings = new ArrayList<>();

            for (Map.Entry<String, Number> entry : measurements.entrySet()) {
                String field = entry.getKey();
                double value = entry.getValue().doubleValue();
                Map<String, Double> fieldSchema = schema.get(field);

                if (fieldSchema == null) continue;

                Double min = fieldSchema.get("min");
                Double max = fieldSchema.get("max");
                Double accessibleMin = fieldSchema.get("accessible_min");
                Double accessibleMax = fieldSchema.get("accessible_max");

                if (min != null && value < min) {
                    throw new MeasurementValidationException(
                            "Field '" + field + "' value " + value + " is below minimum of " + min);
                }
                if (max != null && value > max) {
                    throw new MeasurementValidationException(
                            "Field '" + field + "' value " + value + " exceeds maximum of " + max);
                }
                if (accessibleMax != null && value > accessibleMax) {
                    warnings.add(new MeasurementWarning(field,
                            "Exceeds accessible limit of " + accessibleMax));
                }
                if (accessibleMin != null && value < accessibleMin) {
                    warnings.add(new MeasurementWarning(field,
                            "Below accessible minimum of " + accessibleMin));
                }
            }

            return warnings;

        } catch (MeasurementValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new MeasurementValidationException("Invalid measurements format: " + e.getMessage());
        }
    }
}
