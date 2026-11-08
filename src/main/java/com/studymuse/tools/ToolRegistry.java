package com.studymuse.tools;

import com.studymuse.services.StudyException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds the agent's tools by name, describes them for the prompt, and runs one safely. */
public class ToolRegistry {

    private final Map<String, StudyTool> tools = new LinkedHashMap<>();

    public void register(StudyTool tool) {
        tools.put(tool.getName(), tool);
    }

    public StudyTool get(String name) {
        return name == null ? null : tools.get(name.trim());
    }

    public List<ToolDescription> describeAll() {
        List<ToolDescription> out = new ArrayList<>();
        for (StudyTool t : tools.values()) {
            out.add(t.describe());
        }
        return out;
    }

    public String describeForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (ToolDescription d : describeAll()) {
            sb.append(d.toPromptLine()).append('\n');
        }
        return sb.toString();
    }

    /** Runs a tool; unknown tools, bad arguments and tool failures become an error result instead of an exception. */
    public ToolResult execute(String name, Map<String, Object> args) {
        StudyTool tool = get(name);
        if (tool == null) {
            return ToolResult.fail("Unknown tool '" + name + "'. Available tools: " + String.join(", ", tools.keySet()));
        }
        if (!tool.validateArgs(args)) {
            return ToolResult.fail("Invalid arguments for " + name + ". Expected: " + tool.getParameters());
        }
        try {
            return tool.execute(args);
        } catch (StudyException | IllegalArgumentException e) {
            return ToolResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.fail("The tool failed unexpectedly: " + e);
        }
    }
}
