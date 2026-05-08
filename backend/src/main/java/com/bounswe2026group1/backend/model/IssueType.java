package com.bounswe2026group1.backend.model;

import java.util.EnumSet;
import java.util.Set;

public enum IssueType {
    MISSING(
        "This feature is absent where it should exist.",
        EnumSet.of(ObjectType.RAMP, ObjectType.ELEVATOR, ObjectType.SIDEWALK, ObjectType.DOOR, ObjectType.STAIR)
    ),
    TOO_STEEP(
        "The ramp slope exceeds accessibility limits (>10% for existing, >8% for new construction).",
        EnumSet.of(ObjectType.RAMP)
    ),
    TOO_NARROW(
        "The clear passage width is below the accessible minimum.",
        EnumSet.of(ObjectType.RAMP, ObjectType.SIDEWALK, ObjectType.DOOR, ObjectType.STAIR)
    ),
    MISSING_HANDRAIL(
        "No handrail is present; handrails are required on both sides at 90 cm height.",
        EnumSet.of(ObjectType.RAMP, ObjectType.STAIR)
    ),
    NO_LANDING(
        "No level resting platform (150×150 cm) at the top/bottom of a ramp, or between stair flights.",
        EnumSet.of(ObjectType.RAMP, ObjectType.STAIR)
    ),
    SLIPPERY_SURFACE(
        "The surface is not slip-resistant when wet or dry, creating a fall hazard.",
        EnumSet.of(ObjectType.RAMP, ObjectType.SIDEWALK, ObjectType.STAIR)
    ),
    BLOCKED(
        "An obstacle (parked vehicle, furniture, debris) blocks the walkway.",
        EnumSet.of(ObjectType.SIDEWALK)
    ),
    NO_TACTILE_PAVING(
        "Tactile guidance strips for visually impaired users are missing.",
        EnumSet.of(ObjectType.SIDEWALK)
    ),
    INSUFFICIENT_CLEARANCE(
        "The vertical clearance is below 220 cm (e.g. low-hanging branches, signs).",
        EnumSet.of(ObjectType.SIDEWALK)
    ),
    OUT_OF_SERVICE(
        "The elevator exists but is not operational.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    DOOR_TOO_NARROW(
        "The elevator door opening is less than 90 cm, blocking wheelchair access.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    CABIN_TOO_SMALL(
        "The cabin is narrower than 120 cm or has less than 1.80 m² floor area.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    NO_AUDIO(
        "No audio announcement of floor arrivals; required for visually impaired users.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    NO_GRAB_BAR(
        "No grab bar inside the cabin; required at 80 cm height on at least one side wall.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    INSUFFICIENT_LANDING(
        "The approach area in front of the elevator doors is less than 120–150 cm.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    HIGH_THRESHOLD(
        "The raised sill at the base of the door exceeds 0.6 cm, blocking wheels and causing trips.",
        EnumSet.of(ObjectType.DOOR)
    ),
    STEP_AT_ENTRANCE(
        "One or more steps at the entrance create a barrier where a ramp is needed.",
        EnumSet.of(ObjectType.DOOR)
    ),
    NO_LEVER_HANDLE(
        "The door has a round knob that requires gripping; a lever handle is required.",
        EnumSet.of(ObjectType.DOOR)
    ),
    HEAVY_DOOR(
        "The door requires excessive force to open, making it unusable for many users.",
        EnumSet.of(ObjectType.DOOR)
    ),
    NO_AUTOMATIC_DOOR(
        "Building entrance lacks an automatic/sensor-operated door.",
        EnumSet.of(ObjectType.DOOR)
    ),
    RISER_TOO_HIGH(
        "Individual step height exceeds 16 cm (or 18 cm in buildings with an elevator).",
        EnumSet.of(ObjectType.STAIR)
    ),
    TREAD_TOO_SHALLOW(
        "Step depth (front-to-back) is less than 27 cm, reducing stability.",
        EnumSet.of(ObjectType.STAIR)
    ),
    NO_ANTI_SLIP(
        "Steps lack anti-slip strips (4–5 cm wide, contrasting colour) on their leading edges.",
        EnumSet.of(ObjectType.STAIR)
    ),
    OPEN_RISERS(
        "The vertical face between steps is open, allowing canes/walkers to slip through.",
        EnumSet.of(ObjectType.STAIR)
    );

    private final String description;
    private final Set<ObjectType> validFor;

    IssueType(String description, Set<ObjectType> validFor) {
        this.description = description;
        this.validFor = validFor;
    }

    public String getDescription() {
        return description;
    }

    public Set<ObjectType> getValidFor() {
        return validFor;
    }

    public boolean isValidFor(ObjectType objectType) {
        return validFor.contains(objectType);
    }
}
