package com.studymuse.agent;

import com.studymuse.domain.Chunk;
import com.studymuse.llm.Message;
import com.studymuse.llm.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Design pattern: BUILDER.
 * A prompt has many optional parts (instructions, retrieved chunks, memory, tool list, output format,
 * user request) and each feature needs a different combination. Services chain only the steps they
 * need and call build(), instead of using a constructor with many null arguments.
 */
public class PromptBuilder {

    private final StringBuilder system = new StringBuilder();
    private final List<Chunk> context = new ArrayList<>();
    private String memory;
    private String tools;
    private String outputFormat;
    private String userRequest;

    public PromptBuilder withSystemInstructions(String text) {
        if (system.length() > 0) {
            system.append("\n");
        }
        system.append(text);
        return this;
    }

    public PromptBuilder withContext(List<Chunk> chunks) {
        context.addAll(chunks);
        return this;
    }

    public PromptBuilder withMemory(String text) {
        this.memory = text;
        return this;
    }

    public PromptBuilder withTools(String toolDescriptions) {
        this.tools = toolDescriptions;
        return this;
    }

    /** Asks the LLM to reply with JSON only, matching the given example shape. */
    public PromptBuilder withOutputFormat(String jsonExample) {
        this.outputFormat = jsonExample;
        return this;
    }

    public PromptBuilder withUserRequest(String text) {
        this.userRequest = text;
        return this;
    }

    public Prompt build() {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalStateException("A prompt needs a user request.");
        }
        StringBuilder sys = new StringBuilder(system);
        if (outputFormat != null) {
            sys.append("\n\nOUTPUT FORMAT: reply with a single JSON object only (no markdown, no extra text), shaped like:\n")
                    .append(outputFormat);
        }
        StringBuilder user = new StringBuilder();
        if (memory != null && !memory.isBlank()) {
            user.append("CONVERSATION MEMORY:\n").append(memory).append("\n\n");
        }
        if (tools != null && !tools.isBlank()) {
            user.append("AVAILABLE TOOLS:\n").append(tools).append("\n\n");
        }
        if (!context.isEmpty()) {
            user.append("COURSE MATERIAL (each line starts with its chunk id):\n");
            for (Chunk c : context) {
                user.append('[').append(c.getId()).append("] ").append(c.getText().replace('\n', ' ')).append('\n');
            }
            user.append('\n');
        }
        user.append("REQUEST:\n").append(userRequest);
        return new Prompt(sys.toString(), List.of(new Message("user", user.toString())));
    }
}
