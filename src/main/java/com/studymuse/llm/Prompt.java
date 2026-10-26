package com.studymuse.llm;

import java.util.List;

/** A complete request for the LLM: system instructions plus the conversation messages. Built by PromptBuilder. */
public record Prompt(String system, List<Message> messages) {

    public Prompt {
        messages = List.copyOf(messages);
    }

    /** All text of the prompt, used for logging and by the offline mock provider. */
    public String fullText() {
        StringBuilder sb = new StringBuilder(system == null ? "" : system);
        for (Message m : messages) {
            sb.append("\n\n").append(m.text());
        }
        return sb.toString();
    }
}
