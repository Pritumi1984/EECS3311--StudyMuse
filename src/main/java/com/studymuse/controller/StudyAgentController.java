package com.studymuse.controller;

import com.studymuse.domain.Answer;
import com.studymuse.domain.Concept;
import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.domain.Summary;
import com.studymuse.domain.SummaryLength;
import com.studymuse.services.MaterialAnalysisService;
import com.studymuse.services.MaterialService;
import com.studymuse.services.QAService;

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
    private final MaterialAnalysisService analysisService;
    private final QAService qaService;
    private final String llmName;

    public StudyAgentController(MaterialService materialService, MaterialAnalysisService analysisService,
                                QAService qaService, String llmName) {
        this.materialService = materialService;
        this.analysisService = analysisService;
        this.qaService = qaService;
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

    // ---------------------------------------------------------------- F02 summarization

    public Summary summarize(String courseId, String materialIdOrName, SummaryLength length) {
        Material material = materialService.getMaterial(courseId, materialIdOrName);
        return analysisService.summarize(materialService.getCourse(courseId).getId(), material, length);
    }

    // ---------------------------------------------------------------- F04 key concept extraction

    public List<Concept> extractConcepts(String courseId, String materialIdOrName) {
        Course course = materialService.getCourse(courseId);
        Material material = materialService.getMaterial(courseId, materialIdOrName);
        return analysisService.extractConcepts(course, material);
    }

    // ---------------------------------------------------------------- F03 question answering

    public Answer askQuestion(String courseId, String question) {
        return qaService.answer(materialService.getCourse(courseId).getId(), question);
    }

    public String getLlmName() {
        return llmName;
    }
}
