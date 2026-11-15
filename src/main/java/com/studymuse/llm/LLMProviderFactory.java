package com.studymuse.llm;

import com.studymuse.config.AppConfig;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Design pattern: FACTORY METHOD.
 * Decides at runtime which concrete provider to build from the .env settings and assembles the
 * decorator chain (Logging -> Retrying -> vendor adapter; for Gemini, Logging -> Fallback over
 * several models, each Retrying). Callers only ever see LLMProvider.
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
            // primary model first, then the fallback models (LLM_FALLBACK_MODELS) for when it is busy
            String thinking = config.get("LLM_THINKING", GeminiProvider.DEFAULT_THINKING);
            Set<String> models = new LinkedHashSet<>();
            models.add(config.getModel(GeminiProvider.DEFAULT_MODEL));
            for (String m : config.get("LLM_FALLBACK_MODELS", GeminiProvider.DEFAULT_FALLBACK_MODELS).split(",")) {
                if (!m.isBlank()) {
                    models.add(m.trim());
                }
            }
            List<LLMProvider> chain = new ArrayList<>();
            for (String m : models) {
                chain.add(new RetryingProvider(new GeminiProvider(key, m, thinking), 1, 1500));
            }
            return new LoggingProvider(new FallbackProvider(chain), Paths.get("logs", "llm-calls.log"));
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
