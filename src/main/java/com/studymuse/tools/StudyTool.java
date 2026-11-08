package com.studymuse.tools;

import java.util.Map;

/**
 * A capability the agent can use (F12). Every tool has the same interface, so the agent can choose
 * any tool by name and ToolRegistry runs it the same way. Tools wrap the existing services; the LLM
 * never touches files or data directly.
 */
public interface StudyTool {

    String getName();

    String getDescription();

    /** The JSON arguments the tool expects, written for the LLM, e.g. {"query": "text"}. */
    String getParameters();

    /** Checks the LLM's arguments before anything runs. The courseId argument is always added by the agent. */
    boolean validateArgs(Map<String, Object> args);

    ToolResult execute(Map<String, Object> args);

    default ToolDescription describe() {
        return new ToolDescription(getName(), getDescription(), getParameters());
    }
}
