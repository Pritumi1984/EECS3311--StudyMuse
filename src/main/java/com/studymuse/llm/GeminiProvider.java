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

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** Gemini 3 models think at "high" by default, which is slow; "low" is much faster and enough here. */
    public static final String DEFAULT_THINKING = "low";
    /** Tried in order when the main model is overloaded or unavailable. */
    public static final String DEFAULT_FALLBACK_MODELS = "gemini-3.5-flash-lite,gemini-3.1-flash-lite,gemini-3.6-flash";

    private final String apiKey;
    private final String model;
    private volatile String thinkingLevel;

    public GeminiProvider(String apiKey, String model) {
        this(apiKey, model, DEFAULT_THINKING);
    }

    public GeminiProvider(String apiKey, String model, String thinkingLevel) {
        this.apiKey = apiKey;
        this.model = model;
        this.thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() || !model.startsWith("gemini-3")
                ? null : thinkingLevel.trim().toLowerCase();
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        try {
            return fromResponseJson(post(prompt));
        } catch (LLMException e) {
            // a model that does not accept this thinking setting answers 400: retry once without it
            if (thinkingLevel != null && e.getMessage() != null && e.getMessage().toLowerCase().contains("thinking")) {
                thinkingLevel = null;
                return fromResponseJson(post(prompt));
            }
            throw e;
        }
    }

    private Map<String, Object> post(Prompt prompt) {
        return HttpJson.post(BASE + model + ":generateContent", Map.of("x-goog-api-key", apiKey),
                toRequestJson(prompt), "Gemini");
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
        Map<String, Object> config = new LinkedHashMap<>();
        if (model.startsWith("gemini-3")) {
            // Google recommends the default temperature (1.0) for Gemini 3; lower values can cause looping
            if (thinkingLevel != null) {
                config.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
            }
        } else {
            config.put("temperature", 0.3);
        }
        body.put("generationConfig", config);
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
