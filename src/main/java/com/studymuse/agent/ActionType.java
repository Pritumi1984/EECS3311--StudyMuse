package com.studymuse.agent;

/** What the LLM decided to do next in the agent loop (F12). */
public enum ActionType {
    TOOL_CALL, FINAL_ANSWER, ASK_CLARIFICATION
}
