package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.ObjectType;

import java.util.Map;

public final class MeasurementSchemas {

    private MeasurementSchemas() {}

    private static final Map<ObjectType, String> SCHEMAS = Map.ofEntries(
        Map.entry(ObjectType.RAMP,
            "{\"slope_percent\":{\"min\":0,\"max\":100,\"accessible_max\":10}," +
            "\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":100}," +
            "\"height_cm\":{\"min\":0,\"max\":1000}}"),
        Map.entry(ObjectType.ELEVATOR,
            "{\"door_width_cm\":{\"min\":0,\"max\":300,\"accessible_min\":90}," +
            "\"cabin_width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":120}," +
            "\"cabin_depth_cm\":{\"min\":0,\"max\":500,\"accessible_min\":140}}"),
        Map.entry(ObjectType.SIDEWALK,
            "{\"height_cm\":{\"min\":0,\"max\":100,\"accessible_max\":15}," +
            "\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":150}}"),
        Map.entry(ObjectType.DOOR,
            "{\"width_cm\":{\"min\":0,\"max\":300,\"accessible_min\":90}," +
            "\"threshold_height_cm\":{\"min\":0,\"max\":50,\"accessible_max\":0.6}}"),
        Map.entry(ObjectType.STAIR,
            "{\"riser_cm\":{\"min\":0,\"max\":50,\"accessible_max\":16}," +
            "\"tread_cm\":{\"min\":0,\"max\":100,\"accessible_min\":27}}"),
        Map.entry(ObjectType.PEDESTRIAN_CROSSING,
            "{\"signal_duration_sec\":{\"min\":0,\"max\":120,\"accessible_min\":15}," +
            "\"crossing_width_cm\":{\"min\":0,\"max\":2000,\"accessible_min\":300}," +
            "\"dropped_curb_height_cm\":{\"min\":0,\"max\":50,\"accessible_max\":1.3}}"),
        Map.entry(ObjectType.CURB_RAMP,
            "{\"slope_percent\":{\"min\":0,\"max\":100,\"accessible_max\":8}," +
            "\"width_cm\":{\"min\":0,\"max\":500,\"accessible_min\":120}," +
            "\"lip_height_cm\":{\"min\":0,\"max\":20,\"accessible_max\":1.3}}"),
        Map.entry(ObjectType.WASHROOM,
            "{\"door_width_cm\":{\"min\":0,\"max\":300,\"accessible_min\":90}," +
            "\"turning_space_cm\":{\"min\":0,\"max\":500,\"accessible_min\":150}," +
            "\"sink_height_cm\":{\"min\":0,\"max\":150,\"accessible_max\":85}," +
            "\"grab_bar_height_cm\":{\"min\":0,\"max\":150,\"accessible_min\":70}}"),
        Map.entry(ObjectType.ROOM_SIGN,
            "{\"mounting_height_cm\":{\"min\":0,\"max\":300,\"accessible_min\":120,\"accessible_max\":160}," +
            "\"text_height_mm\":{\"min\":0,\"max\":200,\"accessible_min\":15}}")
    );

    public static String getSchemaJson(ObjectType objectType) {
        return SCHEMAS.get(objectType);
    }
}
