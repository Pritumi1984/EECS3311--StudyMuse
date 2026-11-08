package com.studymuse.tools;

import com.studymuse.services.AnalyticsService;
import com.studymuse.services.MaterialService;

import java.util.Map;

/** Runs weak-topic analysis (F09) and returns the report. */
public class GetWeakTopicsTool implements StudyTool {

    private final AnalyticsService analytics;
    private final MaterialService materials;

    public GetWeakTopicsTool(AnalyticsService analytics, MaterialService materials) {
        this.analytics = analytics;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "get_weak_topics";
    }

    @Override
    public String getDescription() {
        return "Find the student's weakest topics (accuracy below 70% with at least 3 attempts) and why.";
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
        return ToolResult.ok(analytics.findWeakTopics(materials.getCourse(ToolArgs.text(args, "courseId"))).toText());
    }
}
