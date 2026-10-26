package com.studymuse.llm;

/**
 * The single entry point services use to talk to an LLM. It holds the configured provider
 * (STRATEGY context) and returns plain text, so services never depend on a vendor.
 */
public class LLMClient {

    private final LLMProvider provider;

    public LLMClient(LLMProvider provider) {
        this.provider = provider;
    }

    public String complete(Prompt prompt) {
        LLMResponse r = provider.send(prompt);
        if (r.text() == null || r.text().isBlank()) {
            throw new LLMException("The LLM returned an empty answer.", false);
        }
        return r.text();
    }

    public String getProviderName() {
        return provider.getName();
    }
}
