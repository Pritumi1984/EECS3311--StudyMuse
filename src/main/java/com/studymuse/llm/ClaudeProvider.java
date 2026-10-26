package com.studymuse.llm;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Design patterns: ADAPTER and concrete STRATEGY.
 * Adapts Anthropic's Messages API (adaptee) to our LLMProvider interface (target).
 */
public class ClaudeProvider implements LLMProvider {

    public static final String DEFAULT_MODEL = "claude-haiku-5-5";
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final String model;

    public ClaudeProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        Map<String, Object> reply = HttpJson.post(URL,
                Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), toRequestJson(prompt), "Claude");
        return fromResponseJson(reply);
    }

    private Map<String, Object> toRequestJson(Prompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        if (prompt.system() != null && !prompt.system().isBlank()) {
            body.put("system", prompt.system());
        }
        List<Object> messages = new ArrayList<>();
        for (Message m : prompt.messages()) {
            messages.add(Map.of("role", m.role(), "content", m.text()));
        }
        body.put("messages", messages);
        return body;
    }

    private LLMResponse fromResponseJson(Map<String, Object> json) {
        StringBuilder text = new StringBuilder();
        for (Object block : Json.list(json, "content")) {
            Map<String, Object> b = Json.obj(block);
            if ("text".equals(Json.str(b, "type"))) {
                text.append(Json.str(b, "text"));
            }
        }
        Map<String, Object> usage = Json.obj(json.get("usage"));
        return new LLMResponse(text.toString(), Json.integer(usage, "input_tokens", 0),
                Json.integer(usage, "output_tokens", 0), model);
    }

    @Override
    public String getName() {
        return "Claude (" + model + ")";
    }
}
