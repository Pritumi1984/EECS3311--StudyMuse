package com.studymuse.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent's final message plus every step it executed (F12). outcome says how the run ended:
 * FINAL_ANSWER, ASK_CLARIFICATION, STEP_LIMIT or LLM_ERROR.
 */
public record AgentResponse(String answer, List<AgentStep> steps, boolean stoppedAtLimit, String outcome) {

    public AgentResponse {
        steps = List.copyOf(steps);
    }

    public AgentResponse(String answer, List<AgentStep> steps, boolean stoppedAtLimit) {
        this(answer, steps, stoppedAtLimit, stoppedAtLimit ? "STEP_LIMIT" : "FINAL_ANSWER");
    }

    /** Machine-readable form for the CLI's --json output and the HTTP API (used for automated agent tests). */
    public Map<String, Object> toJsonMap() {
        List<Object> stepList = new ArrayList<>();
        int i = 1;
        for (AgentStep s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("step", i++);
            AgentAction a = s.action();
            m.put("action", a == null ? "INVALID_REPLY" : a.type().name());
            if (a != null && a.type() == ActionType.TOOL_CALL) {
                m.put("tool", a.toolName());
                m.put("arguments", a.arguments());
            }
            if (s.result() != null) {
                m.put("success", s.result().success());
                m.put(s.result().success() ? "result" : "error",
                        s.result().success() ? s.result().data() : s.result().error());
            }
            stepList.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("outcome", outcome);
        out.put("answer", answer);
        out.put("toolsUsed", steps.stream().filter(s -> s.action() != null && s.action().type() == ActionType.TOOL_CALL)
                .map(s -> s.action().toolName()).toList());
        out.put("stepCount", steps.size());
        out.put("stoppedAtLimit", stoppedAtLimit);
        out.put("steps", stepList);
        return out;
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
