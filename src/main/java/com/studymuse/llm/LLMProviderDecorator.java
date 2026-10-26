package com.studymuse.llm;

/**
 * Design pattern: DECORATOR (abstract decorator).
 * Wraps another LLMProvider and forwards to it, letting subclasses add behaviour
 * (retrying, logging) to any provider without changing the provider classes.
 */
public abstract class LLMProviderDecorator implements LLMProvider {

    protected final LLMProvider wrapped;

    protected LLMProviderDecorator(LLMProvider wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        return wrapped.send(prompt);
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }
}
