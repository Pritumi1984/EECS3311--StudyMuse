package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** One flashcard review: which card, its topic, the rating and the date. Feeds progress and weak topics. */
public record ReviewRecord(String cardId, String topic, Rating rating, String date) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cardId", cardId);
        m.put("topic", topic);
        m.put("rating", rating.name());
        m.put("date", date);
        return m;
    }

    public static ReviewRecord fromMap(Map<String, Object> m) {
        return new ReviewRecord(Json.str(m, "cardId"), Json.str(m, "topic"), Rating.valueOf(Json.str(m, "rating")),
                Json.str(m, "date"));
    }
}
