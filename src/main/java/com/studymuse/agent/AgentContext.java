package com.studymuse.agent;

import java.util.ArrayList;
import java.util.List;

/** Working state of one agent run: the request and the steps taken so far. */
public class AgentContext {

    private static final int RESULT_CHARS = 1500;

    private final String courseId;
    private final String request;
    private final List<AgentStep> steps = new ArrayList<>();

    public AgentContext(String courseId, String request) {
        this.courseId = courseId;
        this.request = request;
    }

    public String getCourseId() { return courseId; }
    public String getRequest() { return request; }
    public List<AgentStep> getSteps() { return List.copyOf(steps); }

    public void addStep(AgentStep step) {
        steps.add(step);
    }

    public int stepCount() {
        return steps.size();
    }

    /** The steps so far, written for the next prompt, including (truncated) tool results and errors. */
    public String stepsForPrompt() {
        if (steps.isEmpty()) {
            return "(none yet)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (AgentStep s : steps) {
            sb.append("Step ").append(i++).append(": ");
            if (s.action() != null && s.action().type() == ActionType.TOOL_CALL) {
                sb.append("called ").append(s.action().toolName()).append(' ').append(s.action().arguments());
            } else {
                sb.append("(no valid action)");
            }
            String r = s.result() == null ? "" : s.result().success() ? s.result().data() : "ERROR: " + s.result().error();
            if (r.length() > RESULT_CHARS) {
                r = r.substring(0, RESULT_CHARS) + "...";
            }
            sb.append("\nResult: ").append(r).append("\n\n");
        }
        return sb.toString();
    }
}
