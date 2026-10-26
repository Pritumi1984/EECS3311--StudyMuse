package com.studymuse.documents;

import com.studymuse.domain.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic keyword retrieval over a course's chunks (TF-IDF style scoring).
 * This is the "retrieval" part of the agent: AI answers are grounded in the chunks it returns.
 */
public class SearchIndex {

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "are", "was", "with", "that", "this",
            "what", "which", "who", "how", "why", "when", "does", "did", "can", "from", "into", "its", "has", "have",
            "you", "your", "about", "explain", "describe", "tell", "between", "there", "their", "they", "them", "not",
            "but", "all", "any", "use", "used", "using");

    private record Entry(Chunk chunk, Map<String, Integer> termCounts) {
    }

    private final Map<String, List<Entry>> byCourse = new HashMap<>();

    public synchronized void add(String courseId, List<Chunk> chunks) {
        List<Entry> list = byCourse.computeIfAbsent(courseId, k -> new ArrayList<>());
        for (Chunk c : chunks) {
            list.add(new Entry(c, countTerms(c.getText())));
        }
    }

    public synchronized void removeMaterial(String courseId, String materialId) {
        List<Entry> list = byCourse.get(courseId);
        if (list != null) {
            list.removeIf(e -> e.chunk().getMaterialId().equals(materialId));
        }
    }

    /** Returns up to k chunks of the course ranked by relevance; only chunks sharing a keyword are returned. */
    public synchronized List<Chunk> search(String courseId, String query, int k) {
        List<Entry> list = byCourse.getOrDefault(courseId, List.of());
        Map<String, Integer> q = countTerms(query);
        if (list.isEmpty() || q.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> docFreq = new HashMap<>();
        for (Entry e : list) {
            for (String term : q.keySet()) {
                if (e.termCounts().containsKey(term)) {
                    docFreq.merge(term, 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<Chunk, Double>> scored = new ArrayList<>();
        for (Entry e : list) {
            double score = 0;
            for (String term : q.keySet()) {
                Integer tf = e.termCounts().get(term);
                if (tf != null) {
                    double idf = Math.log(1.0 + (double) list.size() / docFreq.get(term));
                    score += (1 + Math.log(tf)) * idf;
                }
            }
            if (score > 0) {
                scored.add(Map.entry(e.chunk(), score));
            }
        }
        scored.sort(Map.Entry.<Chunk, Double>comparingByValue(Comparator.reverseOrder()));
        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            result.add(scored.get(i).getKey());
        }
        return result;
    }

    static Map<String, Integer> countTerms(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 3 || STOP_WORDS.contains(raw)) {
                continue;
            }
            String term = raw.length() > 4 && raw.endsWith("s") ? raw.substring(0, raw.length() - 1) : raw;
            counts.merge(term, 1, Integer::sum);
        }
        return counts;
    }
}
