package com.studymuse.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Concrete DECORATOR: appends one line per LLM call (time, provider, tokens, duration, outcome)
 * to logs/llm-calls.log. The token totals feed the cost table in the Stage 2 report.
 * Prompts and keys are never written to the log.
 */
public class LoggingProvider extends LLMProviderDecorator {

    private final Path logFile;

    public LoggingProvider(LLMProvider wrapped, Path logFile) {
        super(wrapped);
        this.logFile = logFile;
    }

    @Override
    public LLMResponse send(Prompt prompt) {
        long start = System.currentTimeMillis();
        try {
            LLMResponse r = wrapped.send(prompt);
            write(String.format("OK   %s in=%d out=%d %dms", r.model(), r.inputTokens(), r.outputTokens(),
                    System.currentTimeMillis() - start));
            return r;
        } catch (LLMException e) {
            write(String.format("FAIL %s %dms %s", wrapped.getName(), System.currentTimeMillis() - start, e.getMessage()));
            throw e;
        }
    }

    private void write(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Files.writeString(logFile, stamp + " " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging must never break a feature
        }
    }
}
