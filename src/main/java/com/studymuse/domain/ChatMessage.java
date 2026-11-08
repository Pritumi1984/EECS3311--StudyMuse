package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** One turn of the agent chat (F12), stored so the conversation survives restarts. Role is "user" or "assistant". */
public record ChatMessage(String role, String text, String time) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("text", text);
        m.put("time", time);
        return m;
    }

    public static ChatMessage fromMap(Map<String, Object> m) {
        return new ChatMessage(Json.str(m, "role"), Json.str(m, "text"), Json.str(m, "time"));
    }
}
