package com.studymuse.domain;

/** Kinds of quiz questions (F07). Each type is graded by its own GradingStrategy (F08). */
public enum QuestionType {
    MULTIPLE_CHOICE, SHORT_ANSWER;

    /** Accepts "mc", "sa", or the full name. */
    public static QuestionType parse(String text) {
        String t = text == null ? "" : text.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        switch (t) {
            case "MC":
            case "MULTIPLE_CHOICE":
            case "MULTIPLECHOICE":
                return MULTIPLE_CHOICE;
            case "SA":
            case "SHORT_ANSWER":
            case "SHORTANSWER":
                return SHORT_ANSWER;
            default:
                throw new IllegalArgumentException("Question type must be mc or sa (got '" + text + "').");
        }
    }
}
