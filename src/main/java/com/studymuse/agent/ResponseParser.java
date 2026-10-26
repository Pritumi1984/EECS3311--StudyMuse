package com.studymuse.agent;

import com.studymuse.domain.Answer;
import com.studymuse.domain.Chunk;
import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks LLM output before the rest of the system trusts it: extracts and validates JSON, and
 * verifies that cited chunks really exist (so an answer can't invent sources).
 */
public class ResponseParser {

    /** Extracts the JSON object from a reply (tolerating ```json fences or surrounding text). */
    public Map<String, Object> parseJson(String raw) {
        if (raw == null) {
            throw new ResponseFormatException("Empty reply.");
        }
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ResponseFormatException("The reply did not contain a JSON object.");
        }
        try {
            return Json.parseObject(s.substring(start, end + 1));
        } catch (IllegalArgumentException e) {
            throw new ResponseFormatException("The reply was not valid JSON (" + e.getMessage() + ").");
        }
    }

    /** Returns a required non-empty string field or throws ResponseFormatException. */
    public String requireText(Map<String, Object> json, String field) {
        String v = Json.str(json, field);
        if (v == null || v.isBlank()) {
            throw new ResponseFormatException("The reply is missing \"" + field + "\".");
        }
        return v.trim();
    }

    /** Returns the non-blank strings of a list field (missing list = empty). */
    public List<String> textList(Map<String, Object> json, String field) {
        List<String> out = new ArrayList<>();
        for (Object o : Json.list(json, field)) {
            if (o != null && !o.toString().isBlank()) {
                out.add(o.toString().trim());
            }
        }
        return out;
    }

    /**
     * Keeps only citations that match a retrieved chunk. The answer is marked unverified when a
     * citation had to be removed or when no valid citation remains.
     */
    public Answer validateCitations(String answerText, List<String> citedIds, List<Chunk> retrieved) {
        List<Chunk> valid = new ArrayList<>();
        boolean removedAny = false;
        for (String id : citedIds) {
            Chunk match = retrieved.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
            if (match == null) {
                removedAny = true;
            } else if (!valid.contains(match)) {
                valid.add(match);
            }
        }
        return new Answer(answerText, valid, !removedAny && !valid.isEmpty());
    }
}
