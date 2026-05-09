package com.bounswe2026group1.backend.model;

/**
 * A single (ObjectType, IssueType) tuple — the smallest unit a routing constraint
 * cares about when deciding whether a report should become an avoidance polygon.
 */
public record IssueHazard(ObjectType objectType, IssueType issueType) {}
