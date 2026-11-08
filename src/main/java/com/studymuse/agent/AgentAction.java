package com.studymuse.agent;

import java.util.Map;

/** One decision of the LLM: call a tool (with arguments), give the final answer, or ask the student a question. */
public record AgentAction(ActionType type, String toolName, Map<String, Object> arguments, String text,
                          Map<String, Object> remember) {

    public AgentAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        remember = remember == null ? Map.of() : Map.copyOf(remember);
    }
}
