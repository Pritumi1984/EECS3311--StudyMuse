package com.studymuse.llm;

import com.studymuse.config.AppConfig;

import java.nio.file.Paths;

/**
 * Design pattern: FACTORY METHOD.
 * Decides at runtime which concrete provider to build from the .env settings and assembles the
 * decorator chain (Logging -> Retrying -> vendor adapter). Callers only ever see LLMProvider.
 */
public final class LLMProviderFactory {

    private String warning;

    public LLMProvider create(AppConfig config) {
        warning = null;
        String name = config.getProviderName();
        String key = config.getApiKey();
        LLMProvider base;
        if ("mock".equals(name)) {
            base = new MockLLMProvider();
        } else if (key == null) {
            warning = "No LLM_API_KEY in .env - running in offline MOCK mode (answers are placeholders).";
            base = new MockLLMProvider();
        } else if ("gemini".equals(name)) {
            base = new GeminiProvider(key, config.getModel(GeminiProvider.DEFAULT_MODEL));
        } else if ("claude".equals(name) || "anthropic".equals(name)) {
            base = new ClaudeProvider(key, config.getModel(ClaudeProvider.DEFAULT_MODEL));
        } else {
            throw new IllegalArgumentException("Unknown LLM_PROVIDER '" + name + "'. Use gemini, claude or mock.");
        }
        return new LoggingProvider(new RetryingProvider(base, 2, 1500), Paths.get("logs", "llm-calls.log"));
    }

    /** A message for the user when the factory had to fall back to mock mode, otherwise null. */
    public String getWarning() {
        return warning;
    }
}
