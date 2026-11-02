package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A generated practice quiz (F07). */
public class Quiz {
    private final String id;
    private final String title;
    private final Difficulty difficulty;
    private final String createdAt;
    private final List<Question> questions;

    public Quiz(String id, String title, Difficulty difficulty, String createdAt, List<Question> questions) {
        this.id = id;
        this.title = title;
        this.difficulty = difficulty;
        this.createdAt = createdAt;
        this.questions = new ArrayList<>(questions);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getCreatedAt() { return createdAt; }
    public List<Question> getQuestions() { return List.copyOf(questions); }

    public Optional<Question> findQuestion(String questionId) {
        return questions.stream().filter(q -> q.getId().equals(questionId)).findFirst();
    }

    @Override
    public String toString() {
        return title + " (" + questions.size() + " questions, " + difficulty.name().toLowerCase() + ")";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("difficulty", difficulty.name());
        m.put("createdAt", createdAt);
        List<Object> qs = new ArrayList<>();
        for (Question q : questions) {
            qs.add(q.toMap());
        }
        m.put("questions", qs);
        return m;
    }

    public static Quiz fromMap(Map<String, Object> m) {
        List<Question> qs = new ArrayList<>();
        for (Object o : Json.list(m, "questions")) {
            qs.add(Question.fromMap(Json.obj(o)));
        }
        return new Quiz(Json.str(m, "id"), Json.str(m, "title"), Difficulty.valueOf(Json.str(m, "difficulty")),
                Json.str(m, "createdAt"), qs);
    }
}
