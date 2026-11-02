package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One attempt at a quiz (F08): the grade of every question and the overall percentage. */
public class QuizResult {
    private final String id;
    private final String quizId;
    private final String quizTitle;
    private final String takenAt;
    private final List<GradeResult> grades;

    public QuizResult(String id, String quizId, String quizTitle, String takenAt, List<GradeResult> grades) {
        this.id = id;
        this.quizId = quizId;
        this.quizTitle = quizTitle;
        this.takenAt = takenAt;
        this.grades = new ArrayList<>(grades);
    }

    public String getId() { return id; }
    public String getQuizId() { return quizId; }
    public String getQuizTitle() { return quizTitle; }
    public String getTakenAt() { return takenAt; }
    public List<GradeResult> getGrades() { return List.copyOf(grades); }

    public long pendingCount() {
        return grades.stream().filter(GradeResult::isPending).count();
    }

    /** Percentage over graded (non-pending) questions; 0 when nothing could be graded. */
    public double getScorePercent() {
        List<GradeResult> graded = grades.stream().filter(g -> !g.isPending()).toList();
        if (graded.isEmpty()) {
            return 0;
        }
        return 100.0 * graded.stream().mapToDouble(GradeResult::getScore).sum() / graded.size();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder(String.format("%s - score %.0f%%", quizTitle, getScorePercent()));
        if (pendingCount() > 0) {
            sb.append(" (").append(pendingCount()).append(" short answer(s) pending: AI unavailable)");
        }
        sb.append("\n");
        int i = 1;
        for (GradeResult g : grades) {
            sb.append(String.format("%n%d. [%s] %s%n   Your answer: %s%n", i++, g.verdict(), g.getQuestionText(),
                    g.getStudentAnswer().isBlank() ? "(blank)" : g.getStudentAnswer()));
            if (!g.isFullyCorrect()) {
                sb.append("   Correct answer: ").append(g.getCorrectAnswer()).append('\n');
            }
            if (g.getFeedback() != null && !g.getFeedback().isBlank()) {
                sb.append("   Feedback: ").append(g.getFeedback()).append('\n');
            }
        }
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("quizId", quizId);
        m.put("quizTitle", quizTitle);
        m.put("takenAt", takenAt);
        List<Object> gs = new ArrayList<>();
        for (GradeResult g : grades) {
            gs.add(g.toMap());
        }
        m.put("grades", gs);
        m.put("scorePercent", getScorePercent());
        return m;
    }

    public static QuizResult fromMap(Map<String, Object> m) {
        List<GradeResult> gs = new ArrayList<>();
        for (Object o : Json.list(m, "grades")) {
            gs.add(GradeResult.fromMap(Json.obj(o)));
        }
        return new QuizResult(Json.str(m, "id"), Json.str(m, "quizId"), Json.str(m, "quizTitle"), Json.str(m, "takenAt"), gs);
    }
}
