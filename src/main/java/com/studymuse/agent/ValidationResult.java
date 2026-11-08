package com.studymuse.agent;

/** Outcome of checking an LLM tool call before it runs. */
public record ValidationResult(boolean valid, String message) {

    public static ValidationResult ok() {
        return new ValidationResult(true, "");
    }

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }
}
