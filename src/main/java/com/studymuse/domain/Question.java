package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One quiz question. Multiple-choice questions have exactly 4 options; correctAnswer is the right option text. */
public class Question {
    private final String id;
    private final String text;
    private final QuestionType type;
    private final List<String> options;
    private final String correctAnswer;
    private final String topic;

    public Question(String id, String text, QuestionType type, List<String> options, String correctAnswer, String topic) {
        this.id = id;
        this.text = text;
        this.type = type;
        this.options = new ArrayList<>(options);
        this.correctAnswer = correctAnswer;
        this.topic = topic;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public QuestionType getType() { return type; }
    public List<String> getOptions() { return List.copyOf(options); }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getTopic() { return topic; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("text", text);
        m.put("type", type.name());
        m.put("options", options);
        m.put("correctAnswer", correctAnswer);
        m.put("topic", topic);
        return m;
    }

    public static Question fromMap(Map<String, Object> m) {
        List<String> opts = new ArrayList<>();
        for (Object o : Json.list(m, "options")) {
            opts.add(String.valueOf(o));
        }
        return new Question(Json.str(m, "id"), Json.str(m, "text"), QuestionType.valueOf(Json.str(m, "type")), opts,
                Json.str(m, "correctAnswer"), Json.str(m, "topic"));
    }
}
