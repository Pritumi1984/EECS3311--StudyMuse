package com.studymuse.agent;

import com.studymuse.tools.ToolResult;

/** One executed step of the agent: the action and, for tool calls, the tool's result (shown to the student). */
public record AgentStep(AgentAction action, ToolResult result) {

    public String describe() {
        String what = action == null ? "invalid reply" : action.type() == ActionType.TOOL_CALL ? action.toolName()
                : action.type().name().toLowerCase();
        return what + " -> " + (result == null ? "" : result.summary());
    }
}
