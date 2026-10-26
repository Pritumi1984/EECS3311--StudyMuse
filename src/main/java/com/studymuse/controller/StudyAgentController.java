package com.studymuse.controller;

import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.services.MaterialService;

import java.nio.file.Path;
import java.util.List;

/**
 * Design pattern: FACADE.
 * The single entry point used by both the GUI (MainWindow) and the CLI (StudyCLI). It offers one
 * simple method per feature and hides the services, retrieval index and LLM layer behind it, so the
 * two interfaces behave identically and stay thin.
 */
public class StudyAgentController {

    private final MaterialService materialService;
    private final String llmName;

    public StudyAgentController(MaterialService materialService, String llmName) {
        this.materialService = materialService;
        this.llmName = llmName;
    }

    // ---------------------------------------------------------------- F01 course and material upload

    public Course createCourse(String name) {
        return materialService.createCourse(name);
    }

    public List<Course> listCourses() {
        return materialService.listCourses();
    }

    public Course getCourse(String courseId) {
        return materialService.getCourse(courseId);
    }

    public Material uploadMaterial(String courseId, Path file, boolean replaceExisting) {
        return materialService.upload(courseId, file, replaceExisting);
    }

    public String getLlmName() {
        return llmName;
    }
}
