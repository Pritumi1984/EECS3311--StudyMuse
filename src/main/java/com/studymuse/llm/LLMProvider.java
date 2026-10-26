package com.studymuse.llm;

/**
 * Design pattern: STRATEGY (strategy interface).
 * Every LLM vendor, the offline mock and the decorators implement this one interface,
 * so LLMClient can use any of them interchangeably; the choice is made at runtime from .env.
 */
public interface LLMProvider {

    LLMResponse send(Prompt prompt);

    String getName();
}
