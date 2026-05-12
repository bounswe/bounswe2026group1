package com.bounswe2026group1.backend.model;

import java.util.EnumSet;
import java.util.Set;

public enum IssueType {
    MISSING(
        "This feature is absent where it should exist.",
        EnumSet.of(
            ObjectType.RAMP, ObjectType.ELEVATOR, ObjectType.SIDEWALK, ObjectType.DOOR, ObjectType.STAIR,
            ObjectType.PEDESTRIAN_CROSSING, ObjectType.CURB_RAMP, ObjectType.WASHROOM, ObjectType.ROOM_SIGN
        )
    ),
    TOO_STEEP(
        "The ramp slope exceeds accessibility limits (>10% for existing, >8% for new construction).",
        EnumSet.of(ObjectType.RAMP, ObjectType.CURB_RAMP)
    ),
    TOO_NARROW(
        "The clear passage width is below the accessible minimum.",
        EnumSet.of(
            ObjectType.RAMP, ObjectType.SIDEWALK, ObjectType.DOOR, ObjectType.STAIR,
            ObjectType.CURB_RAMP, ObjectType.WASHROOM
        )
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
        EnumSet.of(ObjectType.SIDEWALK, ObjectType.PEDESTRIAN_CROSSING)
    ),
    NO_TACTILE_PAVING(
        "Tactile guidance strips for visually impaired users are missing.",
        EnumSet.of(ObjectType.SIDEWALK, ObjectType.PEDESTRIAN_CROSSING)
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
        EnumSet.of(ObjectType.ELEVATOR, ObjectType.WASHROOM)
    ),
    INSUFFICIENT_LANDING(
        "The approach area in front of the elevator doors is less than 120–150 cm.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    HIGH_THRESHOLD(
        "The raised sill at the base of the door exceeds 0.6 cm, blocking wheels and causing trips.",
        EnumSet.of(ObjectType.DOOR, ObjectType.WASHROOM)
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
    ),

    // --- Door (indoor-relevant additions) ---
    NO_GLASS_MARKING(
        "Large glass surfaces lack the required three contrast bands (10–30 cm, 90–100 cm, 130–140 cm) to prevent collisions.",
        EnumSet.of(ObjectType.DOOR)
    ),
    HANDLE_HEIGHT_INVALID(
        "Door handle is mounted outside the accessible 90–110 cm range.",
        EnumSet.of(ObjectType.DOOR)
    ),
    INTERCOM_INACCESSIBLE(
        "Intercom or doorbell is outside the 90–140 cm height range or not approachable from the side.",
        EnumSet.of(ObjectType.DOOR)
    ),

    // --- Elevator (indoor) ---
    NO_BRAILLE(
        "Control panel or sign lacks Braille / raised tactile labels required for visually impaired users.",
        EnumSet.of(ObjectType.ELEVATOR, ObjectType.ROOM_SIGN)
    ),
    BUTTON_HEIGHT_INVALID(
        "Control buttons are mounted outside the accessible 85–120 cm range.",
        EnumSet.of(ObjectType.ELEVATOR)
    ),
    INSUFFICIENT_LIGHTING(
        "Lighting falls below the required level (cabin floor 100 lux, controls 200 lux, signage 200+ lux).",
        EnumSet.of(ObjectType.ELEVATOR, ObjectType.ROOM_SIGN)
    ),

    // --- Stair ---
    IRREGULAR_STEPS(
        "Step risers or treads vary in size; all steps in a flight must be uniform.",
        EnumSet.of(ObjectType.STAIR)
    ),
    MISSING_NOSING_STRIP(
        "Step edges lack the contrasting non-slip nosing strip (2.5–5 cm wide) required for visibility.",
        EnumSet.of(ObjectType.STAIR)
    ),

    // --- Sidewalk ---
    UNEVEN_SURFACE(
        "Surface is cracked, potholed, or otherwise damaged, creating a trip/fall hazard.",
        EnumSet.of(ObjectType.SIDEWALK)
    ),
    UNRAMPED_LEVEL_DIFFERENCE(
        "A vertical level change greater than 1.3 cm is not chamfered or ramped as required.",
        EnumSet.of(ObjectType.SIDEWALK)
    ),

    // --- Ramp ---
    HANDRAIL_TOO_LOW(
        "Handrail is present but mounted below the required 90 cm height.",
        EnumSet.of(ObjectType.RAMP, ObjectType.STAIR)
    ),
    INSUFFICIENT_LANDING_AREA(
        "Top/bottom landing is smaller than the required 150 × 150 cm maneuvering area.",
        EnumSet.of(ObjectType.RAMP)
    ),

    // --- Pedestrian crossing ---
    NO_AUDIO_SIGNAL(
        "Signalized crossing lacks an audible cue for visually impaired users.",
        EnumSet.of(ObjectType.PEDESTRIAN_CROSSING)
    ),
    SIGNAL_TOO_SHORT(
        "Pedestrian green-light interval is too short to cross safely at accessible walking speed.",
        EnumSet.of(ObjectType.PEDESTRIAN_CROSSING)
    ),
    NO_DROPPED_CURB(
        "Crossing lacks a flush dropped curb on at least one side, blocking wheelchair access.",
        EnumSet.of(ObjectType.PEDESTRIAN_CROSSING)
    ),
    FADED_MARKINGS(
        "Crossing markings are worn or faded, making the crossing hard to see.",
        EnumSet.of(ObjectType.PEDESTRIAN_CROSSING)
    ),
    NO_PEDESTRIAN_REFUGE(
        "Wide crossings lack a central refuge island for slower pedestrians.",
        EnumSet.of(ObjectType.PEDESTRIAN_CROSSING)
    ),

    // --- Curb ramp ---
    NO_TACTILE_WARNING(
        "Curb ramp lacks the truncated-dome / tactile warning surface at the street edge.",
        EnumSet.of(ObjectType.CURB_RAMP)
    ),
    RAISED_LIP(
        "Curb ramp has a raised lip at the gutter that catches wheels.",
        EnumSet.of(ObjectType.CURB_RAMP)
    ),

    // --- Washroom (indoor) ---
    NO_ACCESSIBLE_STALL(
        "No accessible toilet stall is provided (none meet the size and grab-bar requirements).",
        EnumSet.of(ObjectType.WASHROOM)
    ),
    INSUFFICIENT_TURNING_SPACE(
        "Interior turning space is below the required 150 × 150 cm wheelchair turning circle.",
        EnumSet.of(ObjectType.WASHROOM)
    ),
    SINK_TOO_HIGH(
        "Sink rim is above the accessible mounting height, preventing seated approach.",
        EnumSet.of(ObjectType.WASHROOM)
    ),
    NO_EMERGENCY_BUTTON(
        "Accessible stall lacks an emergency call button reachable from the floor.",
        EnumSet.of(ObjectType.WASHROOM)
    ),

    // --- Room sign (indoor wayfinding) ---
    LOW_CONTRAST(
        "Sign text and background lack sufficient colour contrast for low-vision users.",
        EnumSet.of(ObjectType.ROOM_SIGN)
    ),
    TEXT_TOO_SMALL(
        "Sign text is below the legible size required at expected reading distance.",
        EnumSet.of(ObjectType.ROOM_SIGN)
    ),
    INVALID_HEIGHT(
        "Sign is mounted outside the accessible viewing height range (typically 120–160 cm centerline).",
        EnumSet.of(ObjectType.ROOM_SIGN)
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
