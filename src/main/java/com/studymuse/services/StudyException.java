package com.studymuse.services;

/** A problem the user can fix (bad input, missing course, unsupported file). Shown as a friendly message. */
public class StudyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StudyException(String message) {
        super(message);
    }

    public StudyException(String message, Throwable cause) {
        super(message, cause);
    }
}
