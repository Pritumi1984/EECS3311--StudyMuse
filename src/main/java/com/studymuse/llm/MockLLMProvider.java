package com.studymuse.llm;

import com.studymuse.util.Json;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concrete STRATEGY used for offline practice runs and automated tests: no network, no cost.
 * Tests can queue exact replies with {@link #enqueue(String)}. Without queued replies it builds a
 * simple, clearly labelled reply from the course text inside the prompt, so every feature can be
 * exercised without an API key. Real demos use GeminiProvider or ClaudeProvider.
 */
public class MockLLMProvider implements LLMProvider {

    private static final Pattern CHUNK_LINE = Pattern.compile("^\\[([A-Za-z0-9_.-]+-c\\d+)\\]\\s*(.*)$", Pattern.MULTILINE);

    private final Deque<String> scripted = new ArrayDeque<>();

    public void enqueue(String reply) {
        scripted.add(reply);
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        String text = scripted.isEmpty() ? improvise(prompt.fullText()) : scripted.poll();
        return new LLMResponse(text, prompt.fullText().length() / 4, text.length() / 4, "mock");
    }

    private String improvise(String prompt) {
        Map<String, String> chunks = new LinkedHashMap<>();
        Matcher m = CHUNK_LINE.matcher(prompt);
        while (m.find()) {
            chunks.put(m.group(1), m.group(2));
        }
        List<String> sentences = new ArrayList<>();
        for (String body : chunks.values()) {
            for (String s : body.split("(?<=[.!?])\\s+")) {
                if (s.split("\\s+").length >= 5) {
                    sentences.add(s.trim());
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (prompt.contains("\"overview\"")) {
            out.put("overview", "[mock] " + (sentences.isEmpty() ? "No text." : sentences.get(0)));
            out.put("keyPoints", sentences.subList(0, Math.min(4, sentences.size())));
        } else if (prompt.contains("\"citations\"")) {
            String firstId = chunks.isEmpty() ? null : chunks.keySet().iterator().next();
            out.put("answer", "[mock] " + (sentences.isEmpty() ? "No relevant text." : sentences.get(0)));
            out.put("citations", firstId == null ? List.of() : List.of(firstId));
        } else if (prompt.contains("\"concepts\"")) {
            List<Object> concepts = new ArrayList<>();
            for (Map.Entry<String, String> e : chunks.entrySet()) {
                for (String s : e.getValue().split("(?<=[.!?])\\s+")) {
                    int is = s.indexOf(" is ");
                    if (is > 0 && is < 40 && concepts.size() < 8) {
                        concepts.add(Map.of("name", s.substring(0, is).trim(), "definition", s.trim(),
                                "sourceChunkId", e.getKey()));
                    }
                }
            }
            out.put("concepts", concepts);
        } else {
            return "[mock] This is the offline mock provider. Set LLM_PROVIDER and LLM_API_KEY in .env for real answers.";
        }
        return Json.compact(out);
    }

    @Override
    public String getName() {
        return "Mock (offline)";
    }
}
