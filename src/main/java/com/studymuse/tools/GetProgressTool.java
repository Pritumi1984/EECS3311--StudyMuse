package com.studymuse.tools;

import com.studymuse.progress.ProgressTracker;
import com.studymuse.services.MaterialService;

import java.util.Map;

/** Returns the progress summary (F11): quiz scores, flashcards, minutes studied and plan completion. */
public class GetProgressTool implements StudyTool {

    private final ProgressTracker tracker;
    private final MaterialService materials;

    public GetProgressTool(ProgressTracker tracker, MaterialService materials) {
        this.tracker = tracker;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "get_progress";
    }

    @Override
    public String getDescription() {
        return "Get the student's progress: quizzes taken, average score, flashcards mastered, study time, plan completion.";
    }

    @Override
    public String getParameters() {
        return "{} (no arguments)";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        return ToolResult.ok(tracker.getStats(materials.getCourse(ToolArgs.text(args, "courseId"))).toText());
    }
}
