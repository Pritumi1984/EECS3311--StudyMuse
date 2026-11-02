package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Grade for one question (F08). Score is 0, 0.5 or 1. "pending" means the short answer could not be
 * graded because the AI service was unavailable; pending answers are left out of the total score.
 */
public class GradeResult {
    private final String questionId;
    private final String topic;
    private final String questionText;
    private final String studentAnswer;
    private final String correctAnswer;
    private final double score;
    private String feedback;
    private final boolean pending;

    public GradeResult(String questionId, String topic, String questionText, String studentAnswer, String correctAnswer,
                       double score, String feedback, boolean pending) {
        this.questionId = questionId;
        this.topic = topic;
        this.questionText = questionText;
        this.studentAnswer = studentAnswer;
        this.correctAnswer = correctAnswer;
        this.score = score;
        this.feedback = feedback;
        this.pending = pending;
    }

    public static GradeResult pending(Question q, String answer, String why) {
        return new GradeResult(q.getId(), q.getTopic(), q.getText(), answer, q.getCorrectAnswer(), 0, why, true);
    }

    public String getQuestionId() { return questionId; }
    public String getTopic() { return topic; }
    public String getQuestionText() { return questionText; }
    public String getStudentAnswer() { return studentAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public double getScore() { return score; }
    public String getFeedback() { return feedback; }
    public boolean isPending() { return pending; }
    public boolean isFullyCorrect() { return !pending && score >= 1.0; }

    /** Adds the AI explanation for a wrong answer. */
    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String verdict() {
        if (pending) {
            return "PENDING";
        }
        return score >= 1.0 ? "CORRECT" : score > 0 ? "PARTIAL" : "WRONG";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questionId", questionId);
        m.put("topic", topic);
        m.put("questionText", questionText);
        m.put("studentAnswer", studentAnswer);
        m.put("correctAnswer", correctAnswer);
        m.put("score", score);
        m.put("feedback", feedback);
        m.put("pending", pending);
        return m;
    }

    public static GradeResult fromMap(Map<String, Object> m) {
        return new GradeResult(Json.str(m, "questionId"), Json.str(m, "topic"), Json.str(m, "questionText"),
                Json.str(m, "studentAnswer"), Json.str(m, "correctAnswer"), Json.decimal(m, "score", 0),
                Json.str(m, "feedback"), Json.bool(m, "pending", false));
    }
}
