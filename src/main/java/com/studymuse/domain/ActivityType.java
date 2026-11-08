package com.studymuse.domain;

/** What the student does in a study session (F10). */
public enum ActivityType {
    READ_NOTES("Read notes"),
    REVIEW_FLASHCARDS("Review flashcards"),
    TAKE_QUIZ("Take a practice quiz"),
    PRACTICE_QUESTIONS("Practice questions");

    private final String label;

    ActivityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts the enum name or loose text such as "review flashcards"; returns null if unknown. */
    public static ActivityType parseOrNull(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (ActivityType a : values()) {
            if (a.name().equals(t) || a.label.equalsIgnoreCase(text.trim())) {
                return a;
            }
        }
        return null;
    }
}
