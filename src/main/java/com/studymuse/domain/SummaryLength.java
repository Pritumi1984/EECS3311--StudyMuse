package com.studymuse.domain;

/** How long a summary should be (F02). */
public enum SummaryLength {
    SHORT("about 3 key points and a 2-sentence overview"),
    MEDIUM("about 6 key points and a 4-sentence overview"),
    DETAILED("about 10 key points and a full-paragraph overview");

    private final String instruction;

    SummaryLength(String instruction) {
        this.instruction = instruction;
    }

    public String getInstruction() {
        return instruction;
    }

    public static SummaryLength parse(String text) {
        if (text == null || text.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Length must be short, medium or detailed (got '" + text + "').");
        }
    }
}
