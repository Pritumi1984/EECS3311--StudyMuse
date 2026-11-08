package com.studymuse.agent;

import com.studymuse.domain.Answer;
import com.studymuse.domain.Chunk;
import com.studymuse.tools.StudyTool;
import com.studymuse.tools.ToolRegistry;
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

    /** F12: reads the LLM's next action: {"action", "tool", "arguments", "text", "remember"}. */
    public AgentAction parseAction(String raw) {
        Map<String, Object> json = parseJson(raw);
        String type = Json.str(json, "action");
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseFormatException("\"action\" must be TOOL_CALL, FINAL_ANSWER or ASK_CLARIFICATION.");
        }
        String text = Json.str(json, "text");
        if (actionType != ActionType.TOOL_CALL && (text == null || text.isBlank())) {
            throw new ResponseFormatException("A " + actionType + " needs a \"text\".");
        }
        return new AgentAction(actionType, Json.str(json, "tool"), Json.obj(json.get("arguments")), text,
                Json.obj(json.get("remember")));
    }

    /** F12: a tool call is only executed if the tool exists and its arguments pass the tool's own checks. */
    public ValidationResult validateToolCall(AgentAction action, ToolRegistry registry) {
        if (action.toolName() == null || action.toolName().isBlank()) {
            return ValidationResult.invalid("A TOOL_CALL needs a \"tool\" name.");
        }
        StudyTool tool = registry.get(action.toolName());
        if (tool == null) {
            return ValidationResult.invalid("Unknown tool '" + action.toolName() + "'.");
        }
        if (!tool.validateArgs(action.arguments())) {
            return ValidationResult.invalid("Invalid arguments for " + tool.getName() + ". Expected: " + tool.getParameters());
        }
        return ValidationResult.ok();
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
