package com.studymuse.tools;

import com.studymuse.domain.Course;
import com.studymuse.domain.PlanRequest;
import com.studymuse.domain.StudyPlan;
import com.studymuse.services.AnalyticsService;
import com.studymuse.services.MaterialService;
import com.studymuse.services.StudyPlanService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Builds and saves a study plan (F10), using the current weak topics like the Study Plan tab does. */
public class CreateStudyPlanTool implements StudyTool {

    private final StudyPlanService planner;
    private final AnalyticsService analytics;
    private final MaterialService materials;

    public CreateStudyPlanTool(StudyPlanService planner, AnalyticsService analytics, MaterialService materials) {
        this.planner = planner;
        this.analytics = analytics;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "create_study_plan";
    }

    @Override
    public String getDescription() {
        return "Create and save a day-by-day study plan until the exam date, prioritizing weak topics.";
    }

    @Override
    public String getParameters() {
        return "{\"examDate\": \"YYYY-MM-DD\", \"hoursPerDay\": number, \"topics\": [\"topic\"] (optional)}";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        Double hours = ToolArgs.decimal(args, "hoursPerDay");
        if (!ToolArgs.hasText(args, "examDate") || hours == null || hours <= 0) {
            return false;
        }
        try {
            LocalDate.parse(ToolArgs.text(args, "examDate"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Course course = materials.getCourse(ToolArgs.text(args, "courseId"));
        PlanRequest req = new PlanRequest(LocalDate.parse(ToolArgs.text(args, "examDate")),
                ToolArgs.decimal(args, "hoursPerDay"), ToolArgs.list(args, "topics"));
        if (!req.examDate().isAfter(LocalDate.now())) {
            return ToolResult.fail("The exam date must be after today.");
        }
        StudyPlan plan = planner.generatePlan(course, req, analytics.findWeakTopics(course).weakTopics(), LocalDate.now());
        return ToolResult.ok(plan.toText());
    }
}
