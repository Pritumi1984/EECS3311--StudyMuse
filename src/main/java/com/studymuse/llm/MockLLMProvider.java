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
        } else if (prompt.contains("\"questions\"")) {
            out.put("questions", mockQuestions(prompt, sentences));
        } else if (prompt.contains("\"cards\"")) {
            int n = requestedCount(prompt, 5);
            List<Object> cards = new ArrayList<>();
            for (int i = 0; i < sentences.size() && cards.size() < n; i++) {
                String s = sentences.get(i);
                int is = s.indexOf(" is ");
                String front = is > 0 && is < 40 ? "What is " + s.substring(0, is).replaceFirst("^(The|A|An) ", "") + "?"
                        : "Complete: " + String.join(" ", java.util.Arrays.copyOfRange(s.split("\\s+"), 0, 5)) + " ...";
                cards.add(Map.of("front", "[mock] " + front, "back", s, "topic", is > 0 && is < 40 ? s.substring(0, is) : "General"));
            }
            out.put("cards", cards);
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

    private static List<Object> mockQuestions(String prompt, List<String> sentences) {
        int n = requestedCount(prompt, 5);
        String[] topics = lineAfter(prompt, "TOPICS:", "General").split("\\s*\\|\\s*");
        String typesLine = lineAfter(prompt, "TYPES:", "MULTIPLE_CHOICE");
        List<String> types = new ArrayList<>();
        if (typesLine.contains("MULTIPLE_CHOICE")) {
            types.add("MULTIPLE_CHOICE");
        }
        if (typesLine.contains("SHORT_ANSWER")) {
            types.add("SHORT_ANSWER");
        }
        List<Object> qs = new ArrayList<>();
        for (int i = 0; i < n && !sentences.isEmpty(); i++) {
            String topic = topics[i % topics.length];
            String type = types.get(i % types.size());
            String correct = shorten(sentences.get(i % sentences.size()));
            Map<String, Object> q = new LinkedHashMap<>();
            if (type.equals("MULTIPLE_CHOICE")) {
                List<String> options = new ArrayList<>();
                options.add(correct);
                for (int k = 1; options.size() < 4 && k < sentences.size(); k++) {
                    String d = shorten(sentences.get((i + k * 3) % sentences.size()));
                    if (!options.contains(d)) {
                        options.add(d);
                    }
                }
                java.util.Collections.rotate(options, i % 4);
                q.put("text", "[mock] Which statement about " + topic + " appears in your notes? (" + (i + 1) + ")");
                q.put("options", options);
            } else {
                q.put("text", "[mock] In your own words: " + String.join(" ", java.util.Arrays.copyOfRange(
                        correct.split("\\s+"), 0, Math.min(6, correct.split("\\s+").length))) + " ...? (" + (i + 1) + ")");
                q.put("options", List.of());
            }
            q.put("type", type);
            q.put("correctAnswer", correct);
            q.put("topic", topic);
            qs.add(q);
        }
        return qs;
    }

    private static String lineAfter(String prompt, String label, String def) {
        Matcher m = Pattern.compile(Pattern.quote(label) + "\\s*(.+)").matcher(prompt);
        return m.find() ? m.group(1).trim() : def;
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 87) + "..." : s;
    }

    /** Reads N from "exactly N" in the request, so mock output respects the requested size. */
    private static int requestedCount(String prompt, int def) {
        Matcher m = Pattern.compile("exactly (\\d+)").matcher(prompt);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    @Override
    public String getName() {
        return "Mock (offline)";
    }
}
