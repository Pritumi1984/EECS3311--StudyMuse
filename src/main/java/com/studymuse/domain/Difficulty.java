package com.studymuse.domain;

/** Quiz difficulty (F07). */
public enum Difficulty {
    EASY, MEDIUM, HARD;

    public static Difficulty parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Difficulty must be easy, medium or hard (got '" + text + "').");
        }
    }
}
