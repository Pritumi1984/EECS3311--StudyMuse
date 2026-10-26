package com.studymuse.llm;

/** Text returned by a provider plus token usage (used for the cost log). */
public record LLMResponse(String text, int inputTokens, int outputTokens, String model) {
}
