package com.studymuse.tools;

/** What a tool returns to the agent: either data (text the LLM reads next) or an error message. */
public record ToolResult(boolean success, String data, String error) {

    public static ToolResult ok(String data) {
        return new ToolResult(true, data, null);
    }

    public static ToolResult fail(String error) {
        return new ToolResult(false, null, error);
    }

    /** Short form shown to the student in the list of agent steps. */
    public String summary() {
        String text = success ? data : "ERROR: " + error;
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 140 ? oneLine.substring(0, 137) + "..." : oneLine;
    }
}
