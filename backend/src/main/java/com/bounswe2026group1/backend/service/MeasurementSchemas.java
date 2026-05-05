package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.ObjectType;

import java.util.Map;

public final class MeasurementSchemas {

    private MeasurementSchemas() {}

    private static final Map<ObjectType, String> SCHEMAS = Map.of(
        ObjectType.RAMP,
            "{\"slope_percent\":{\"min\":0,\"max\":100,\"accessible_max\":10}," +
            "\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":100}," +
            "\"height_cm\":{\"min\":0,\"max\":1000}}",
        ObjectType.ELEVATOR,
            "{\"door_width_cm\":{\"min\":0,\"max\":300,\"accessible_min\":90}," +
            "\"cabin_width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":120}," +
            "\"cabin_depth_cm\":{\"min\":0,\"max\":500,\"accessible_min\":140}}",
        ObjectType.SIDEWALK,
            "{\"height_cm\":{\"min\":0,\"max\":100,\"accessible_max\":15}," +
            "\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":150}}",
        ObjectType.DOOR,
            "{\"width_cm\":{\"min\":0,\"max\":300,\"accessible_min\":90}," +
            "\"threshold_height_cm\":{\"min\":0,\"max\":50,\"accessible_max\":0.6}}",
        ObjectType.STAIR,
            "{\"riser_cm\":{\"min\":0,\"max\":50,\"accessible_max\":16}," +
            "\"tread_cm\":{\"min\":0,\"max\":100,\"accessible_min\":27}}"
    );

    public static String getSchemaJson(ObjectType objectType) {
        return SCHEMAS.get(objectType);
    }
}
