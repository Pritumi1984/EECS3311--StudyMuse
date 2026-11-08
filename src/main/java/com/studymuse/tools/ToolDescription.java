package com.studymuse.tools;

/** How a tool is described to the LLM: name, what it does, and its JSON arguments. */
public record ToolDescription(String name, String description, String parameters) {

    public String toPromptLine() {
        return "- " + name + ": " + description + " Arguments: " + parameters;
    }
}
