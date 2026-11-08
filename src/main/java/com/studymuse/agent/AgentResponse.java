package com.studymuse.agent;

import java.util.List;

/** The agent's final message plus every step it executed (F12). */
public record AgentResponse(String answer, List<AgentStep> steps, boolean stoppedAtLimit) {

    public AgentResponse {
        steps = List.copyOf(steps);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder(answer).append('\n');
        if (!steps.isEmpty()) {
            sb.append("\nSteps taken:\n");
            int i = 1;
            for (AgentStep s : steps) {
                sb.append("  ").append(i++).append(". ").append(s.describe()).append('\n');
            }
        }
        return sb.toString();
    }
}
