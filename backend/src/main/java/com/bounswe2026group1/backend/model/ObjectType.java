package com.bounswe2026group1.backend.model;

public enum ObjectType {
    RAMP("An inclined surface connecting two levels, used in place of or alongside stairs."),
    ELEVATOR("A vertical lift providing access between floors."),
    SIDEWALK("A paved pedestrian path alongside a road or within a site."),
    DOOR("A door or entrance, including building entrances and interior doors."),
    STAIR("A flight of steps connecting different floor levels.");

    private final String description;

    ObjectType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
