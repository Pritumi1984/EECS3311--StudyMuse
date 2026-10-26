package com.studymuse.domain;

import java.util.ArrayList;
import java.util.List;

/** Result of grounded question answering (F03): the answer text and the chunks it cites. */
public class Answer {
    private final String text;
    private final List<Chunk> sources;
    private final boolean verified;

    public Answer(String text, List<Chunk> sources, boolean verified) {
        this.text = text;
        this.sources = new ArrayList<>(sources);
        this.verified = verified;
    }

    public String getText() { return text; }
    public List<Chunk> getSources() { return List.copyOf(sources); }

    /** False when the materials did not cover the question or the LLM cited chunks that do not exist. */
    public boolean isVerified() { return verified; }

    public String toText() {
        StringBuilder sb = new StringBuilder(text).append("\n");
        if (!sources.isEmpty()) {
            sb.append("\nSources:\n");
            for (Chunk c : sources) {
                sb.append("  - ").append(c.reference()).append('\n');
            }
        }
        if (!verified) {
            sb.append("\n(Unverified: no valid source in your materials supports this answer.)\n");
        }
        return sb.toString();
    }
}
