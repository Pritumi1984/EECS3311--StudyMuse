package com.studymuse.agent;

import com.studymuse.llm.LLMClient;
import com.studymuse.llm.LLMException;
import com.studymuse.tools.ToolRegistry;
import com.studymuse.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * F12 agent loop (SD-08). For each step the LLM sees the request, memory, tool list and the steps so far,
 * and returns one action. Tool calls are validated before they run; invalid replies or tool errors are
 * fed back so the LLM can correct itself. The loop ends on a final answer, a clarifying question, or
 * after maxSteps steps. The agent can only act on the course the student selected.
 */
public class AgentPlanner {

    public static final int DEFAULT_MAX_STEPS = 10;
    static final String ACTION_FORMAT = "{\"action\": \"TOOL_CALL | FINAL_ANSWER | ASK_CLARIFICATION\", "
            + "\"tool\": \"tool name (TOOL_CALL only)\", \"arguments\": {}, "
            + "\"text\": \"your answer or question (FINAL_ANSWER / ASK_CLARIFICATION)\", "
            + "\"remember\": {\"fact name\": \"value\"} (optional: facts worth remembering, e.g. exam date)}";

    private final LLMClient llm;
    private final ToolRegistry tools;
    private final MemoryManager memory;
    private final ResponseParser parser;
    private final int maxSteps;

    public AgentPlanner(LLMClient llm, ToolRegistry tools, MemoryManager memory, ResponseParser parser, int maxSteps) {
        this.llm = llm;
        this.tools = tools;
        this.memory = memory;
        this.parser = parser;
        this.maxSteps = maxSteps;
    }

    public AgentResponse run(String courseId, String request) {
        memory.addMessage(courseId, MemoryManager.message("user", request));
        AgentContext ctx = new AgentContext(courseId, request);
        String answer = null;
        while (ctx.stepCount() < maxSteps) {
            AgentAction action;
            try {
                action = decideNextAction(ctx);
            } catch (ResponseFormatException e) {
                // the error goes back to the LLM in the next prompt so it can correct its format
                ctx.addStep(new AgentStep(null, ToolResult.fail("Your reply was not a valid action: " + e.getMessage())));
                continue;
            } catch (LLMException e) {
                answer = "I couldn't reach the AI service (" + e.getMessage() + "). "
                        + (ctx.stepCount() > 0 ? "Steps completed so far are listed below." : "Please try again in a moment.");
                break;
            }
            remember(courseId, action);
            if (action.type() == ActionType.FINAL_ANSWER || action.type() == ActionType.ASK_CLARIFICATION) {
                answer = action.text().trim();
                break;
            }
            ValidationResult check = parser.validateToolCall(action, tools);
            if (!check.valid()) {
                ctx.addStep(new AgentStep(action, ToolResult.fail(check.message())));
                continue;
            }
            Map<String, Object> args = new LinkedHashMap<>(action.arguments());
            args.put("courseId", courseId); // never trust a course id from the LLM
            ctx.addStep(new AgentStep(action, tools.execute(action.toolName(), args)));
        }
        boolean stopped = answer == null;
        if (stopped) {
            answer = "I stopped after " + maxSteps + " steps without finishing. Here is what I completed so far; "
                    + "you can ask me to continue.";
        }
        memory.addMessage(courseId, MemoryManager.message("assistant", answer));
        return new AgentResponse(answer, ctx.getSteps(), stopped);
    }

    AgentAction decideNextAction(AgentContext ctx) {
        String raw = llm.complete(new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, an AI study agent for one university course. Work step by "
                        + "step: call ONE tool per reply, read its result in the next turn, and continue until the "
                        + "request is done; then give a FINAL_ANSWER that summarizes what you did and the key results. "
                        + "Only state facts that come from tool results or the student's materials. If a tool fails, "
                        + "explain it or try another approach - never invent a result. If information you need is "
                        + "missing (for example the exam date for a study plan) and not in memory, use "
                        + "ASK_CLARIFICATION. Use \"remember\" to store lasting facts the student tells you.")
                .withMemory(memory.getRelevantContext(ctx.getCourseId(), ctx.getRequest()))
                .withTools(tools.describeForPrompt())
                .withOutputFormat(ACTION_FORMAT)
                .withUserRequest(ctx.getRequest() + "\n\nSTEPS SO FAR:\n" + ctx.stepsForPrompt())
                .build());
        return parser.parseAction(raw);
    }

    private void remember(String courseId, AgentAction action) {
        action.remember().forEach((k, v) -> memory.rememberFact(courseId, k, v == null ? null : v.toString()));
    }
}
