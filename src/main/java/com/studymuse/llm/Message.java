package com.studymuse.llm;

/** One chat turn sent to the LLM. Role is "user" or "assistant". */
public record Message(String role, String text) {
}
