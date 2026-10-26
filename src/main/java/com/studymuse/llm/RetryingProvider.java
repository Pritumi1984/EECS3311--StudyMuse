package com.studymuse.llm;

/** Concrete DECORATOR: retries timeouts, rate limits and server errors (up to maxRetries extra attempts). */
public class RetryingProvider extends LLMProviderDecorator {

    private final int maxRetries;
    private final long baseDelayMillis;

    public RetryingProvider(LLMProvider wrapped, int maxRetries, long baseDelayMillis) {
        super(wrapped);
        this.maxRetries = maxRetries;
        this.baseDelayMillis = baseDelayMillis;
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        LLMException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return wrapped.send(prompt);
            } catch (LLMException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxRetries) {
                    break;
                }
                try {
                    Thread.sleep(baseDelayMillis * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }
}
