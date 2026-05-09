package com.bounswe2026group1.backend.model;

public enum ObjectType {
    RAMP("An inclined surface connecting two levels, used in place of or alongside stairs."),
    ELEVATOR("A vertical lift providing access between floors."),
    SIDEWALK("A paved pedestrian path alongside a road or within a site."),
    DOOR("A door or entrance, including building entrances and interior doors."),
    STAIR("A flight of steps connecting different floor levels."),
    PEDESTRIAN_CROSSING("A marked or signalized pedestrian crossing where a sidewalk meets a roadway."),
    CURB_RAMP("A short ramp cut into a curb that allows wheelchairs and strollers to traverse the kerb at crossings."),
    WASHROOM("An accessible toilet/restroom, including stalls, sinks, and grab bars."),
    ROOM_SIGN("A sign or plaque identifying a room or interior space, including Braille and tactile labels.");

    private final String description;

    ObjectType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
