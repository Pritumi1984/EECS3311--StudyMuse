package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** A topic's measured accuracy (0-1) over its attempts, plus the AI's diagnosis of the mistakes (F09). */
public record WeakTopic(String topic, double accuracy, int attempts, String diagnosis) {

    public WeakTopic withDiagnosis(String text) {
        return new WeakTopic(topic, accuracy, attempts, text);
    }

    public int accuracyPercent() {
        return (int) Math.round(accuracy * 100);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("topic", topic);
        m.put("accuracy", accuracy);
        m.put("attempts", attempts);
        m.put("diagnosis", diagnosis);
        return m;
    }

    public static WeakTopic fromMap(Map<String, Object> m) {
        return new WeakTopic(Json.str(m, "topic"), Json.decimal(m, "accuracy", 0), Json.integer(m, "attempts", 0),
                Json.str(m, "diagnosis"));
    }
}
