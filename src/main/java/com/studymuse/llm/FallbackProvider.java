package com.studymuse.llm;

import java.util.List;

/**
 * Tries a list of providers in order (for example several Gemini models). When one is overloaded,
 * rate-limited, times out or no longer exists, the next one is tried, so a busy model does not stop
 * the student. Other errors (bad key, blocked prompt) are reported immediately. It implements
 * LLMProvider, so callers cannot tell it apart from a single provider.
 */
public class FallbackProvider implements LLMProvider {

    private final List<LLMProvider> providers;

    public FallbackProvider(List<LLMProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider is needed.");
        }
        this.providers = List.copyOf(providers);
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        LLMException first = null;
        for (LLMProvider p : providers) {
            try {
                return p.send(prompt);
            } catch (LLMException e) {
                if (first == null) {
                    first = e;
                }
                if (!canFallBack(e)) {
                    throw e;
                }
            }
        }
        throw first;
    }

    static boolean canFallBack(LLMException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        return e.isRetryable() || m.contains("error 404");
    }

    @Override
    public String getName() {
        return providers.get(0).getName() + (providers.size() > 1 ? " +" + (providers.size() - 1) + " fallback" : "");
    }
}
