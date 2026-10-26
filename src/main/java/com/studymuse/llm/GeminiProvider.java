package com.studymuse.llm;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Design patterns: ADAPTER and concrete STRATEGY.
 * Adapts Google's Gemini generateContent REST API (adaptee) to our LLMProvider interface (target),
 * so the rest of StudyMuse never sees Gemini's request/response JSON format.
 */
public class GeminiProvider implements LLMProvider {

    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;

    public GeminiProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        Map<String, Object> reply = HttpJson.post(BASE + model + ":generateContent",
                Map.of("x-goog-api-key", apiKey), toRequestJson(prompt), "Gemini");
        return fromResponseJson(reply);
    }

    private Map<String, Object> toRequestJson(Prompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (prompt.system() != null && !prompt.system().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.system()))));
        }
        List<Object> contents = new ArrayList<>();
        for (Message m : prompt.messages()) {
            String role = "assistant".equals(m.role()) ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", m.text()))));
        }
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", 0.3));
        return body;
    }

    private LLMResponse fromResponseJson(Map<String, Object> json) {
        List<Object> candidates = Json.list(json, "candidates");
        if (candidates.isEmpty()) {
            String reason = Json.str(Json.obj(json.get("promptFeedback")), "blockReason");
            throw new LLMException("Gemini returned no answer" + (reason == null ? "." : " (blocked: " + reason + ")."), false);
        }
        Map<String, Object> content = Json.obj(Json.obj(candidates.get(0)).get("content"));
        StringBuilder text = new StringBuilder();
        for (Object part : Json.list(content, "parts")) {
            String t = Json.str(Json.obj(part), "text");
            if (t != null) {
                text.append(t);
            }
        }
        Map<String, Object> usage = Json.obj(json.get("usageMetadata"));
        return new LLMResponse(text.toString(), Json.integer(usage, "promptTokenCount", 0),
                Json.integer(usage, "candidatesTokenCount", 0), model);
    }

    @Override
    public String getName() {
        return "Gemini (" + model + ")";
    }
}
