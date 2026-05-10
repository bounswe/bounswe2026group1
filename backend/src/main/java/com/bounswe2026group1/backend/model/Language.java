package com.bounswe2026group1.backend.model;

/**
 * UI language preference persisted on {@link RegisteredUser}. New users
 * default to {@link #EN}; clients send the wire form as the enum name
 * ("EN" / "TR") in {@code UpdateLanguageRequest}.
 */
public enum Language {
    EN, TR
}
