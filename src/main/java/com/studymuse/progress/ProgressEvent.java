package com.studymuse.progress;

/** Something that changed the student's progress (F11). */
public record ProgressEvent(String courseId, Type type, String description) {

    public enum Type {
        QUIZ_GRADED, CARD_REVIEWED, PLAN_SESSION_UPDATED
    }
}
