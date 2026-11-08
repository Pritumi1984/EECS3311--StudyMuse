package com.studymuse.controller;

import com.studymuse.domain.Answer;
import com.studymuse.domain.Concept;
import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Rating;
import com.studymuse.domain.Material;
import com.studymuse.domain.PlanRequest;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.StudySession;
import com.studymuse.progress.ProgressObserver;
import com.studymuse.progress.ProgressStats;
import com.studymuse.progress.ProgressTracker;
import com.studymuse.domain.Quiz;
import com.studymuse.domain.QuizRequest;
import com.studymuse.domain.QuizResult;
import com.studymuse.domain.Summary;
import com.studymuse.domain.SummaryLength;
import com.studymuse.domain.WeakTopicReport;
import com.studymuse.services.AnalyticsService;
import com.studymuse.services.FlashcardService;
import com.studymuse.services.MaterialAnalysisService;
import com.studymuse.services.MaterialService;
import com.studymuse.services.QAService;
import com.studymuse.services.QuizService;
import com.studymuse.services.StudyException;
import com.studymuse.services.StudyPlanService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final FlashcardService flashcardService;
    private final QuizService quizService;
    private final AnalyticsService analyticsService;
    private final StudyPlanService studyPlanService;
    private final ProgressTracker progressTracker;
    private final String llmName;

    public StudyAgentController(MaterialService materialService, MaterialAnalysisService analysisService,
                                QAService qaService, FlashcardService flashcardService, QuizService quizService,
                                AnalyticsService analyticsService, StudyPlanService studyPlanService,
                                ProgressTracker progressTracker, String llmName) {
        this.materialService = materialService;
        this.analysisService = analysisService;
        this.qaService = qaService;
        this.flashcardService = flashcardService;
        this.quizService = quizService;
        this.analyticsService = analyticsService;
        this.studyPlanService = studyPlanService;
        this.progressTracker = progressTracker;
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

    // ---------------------------------------------------------------- F05 flashcard generation

    /** Returns an unsaved preview deck; call saveDeck after the student has reviewed it. */
    public Deck generateFlashcards(String courseId, String sourceIdOrConcept, int count) {
        return flashcardService.generate(materialService.getCourse(courseId), sourceIdOrConcept, count);
    }

    public Deck saveDeck(String courseId, Deck deck) {
        return flashcardService.saveDeck(materialService.getCourse(courseId).getId(), deck);
    }

    public List<Deck> listDecks(String courseId) {
        return materialService.getCourse(courseId).getDecks();
    }

    // ---------------------------------------------------------------- F06 spaced-repetition review

    public List<Flashcard> getDueCards(String courseId, LocalDate today) {
        return flashcardService.getDueCards(materialService.getCourse(courseId), today);
    }

    public Optional<LocalDate> nextDueDate(String courseId) {
        return flashcardService.nextDueDate(materialService.getCourse(courseId));
    }

    public Flashcard reviewCard(String courseId, String cardId, Rating rating) {
        return flashcardService.review(materialService.getCourse(courseId), cardId, rating, LocalDate.now());
    }

    // ---------------------------------------------------------------- F07 quiz generation

    public Quiz generateQuiz(String courseId, QuizRequest request) {
        return quizService.generate(materialService.getCourse(courseId), request);
    }

    public List<Quiz> listQuizzes(String courseId) {
        return materialService.getCourse(courseId).getQuizzes();
    }

    public Quiz getQuiz(String courseId, String quizId) {
        Course course = materialService.getCourse(courseId);
        return course.findQuiz(quizId)
                .orElseThrow(() -> new StudyException("No quiz '" + quizId + "' in " + course.getName() + ". Use 'quizzes'."));
    }

    // ---------------------------------------------------------------- F08 grading and feedback

    /** answers: question id -> the student's answer (option text, letter A-D, or free text). */
    public QuizResult submitQuiz(String courseId, String quizId, Map<String, String> answers) {
        Course course = materialService.getCourse(courseId);
        return quizService.grade(course, getQuiz(courseId, quizId), answers);
    }

    public List<QuizResult> listQuizResults(String courseId) {
        return materialService.getCourse(courseId).getQuizResults();
    }

    // ---------------------------------------------------------------- F09 weak-topic identification

    public WeakTopicReport getWeakTopics(String courseId) {
        return analyticsService.findWeakTopics(materialService.getCourse(courseId));
    }

    // ---------------------------------------------------------------- F10 study plan

    /** SD-07: the controller first gets the weak topics (F09), then passes them to the planner. */
    public StudyPlan generatePlan(String courseId, PlanRequest request) {
        Course course = materialService.getCourse(courseId);
        if (request.examDate() == null || !request.examDate().isAfter(LocalDate.now())) {
            throw new StudyException("The exam date must be after today.");
        }
        List<com.studymuse.domain.WeakTopic> weak = analyticsService.findWeakTopics(course).weakTopics();
        return studyPlanService.generatePlan(materialService.getCourse(courseId), request, weak, LocalDate.now());
    }

    public Optional<StudyPlan> getPlan(String courseId) {
        return Optional.ofNullable(materialService.getCourse(courseId).getStudyPlan());
    }

    // ---------------------------------------------------------------- F11 progress tracking

    public ProgressStats getProgress(String courseId) {
        return progressTracker.getStats(materialService.getCourse(courseId));
    }

    /** Lets a view subscribe to progress events (OBSERVER) without touching the services. */
    public void addProgressObserver(ProgressObserver observer) {
        progressTracker.addObserver(observer);
    }

    public StudySession setPlanSessionDone(String courseId, LocalDate date, int position, boolean done) {
        return studyPlanService.setSessionDone(materialService.getCourse(courseId), date, position, done);
    }

    public String getLlmName() {
        return llmName;
    }
}
