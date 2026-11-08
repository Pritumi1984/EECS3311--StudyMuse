package com.studymuse.tools;

import com.studymuse.documents.SearchIndex;
import com.studymuse.domain.Chunk;

import java.util.List;
import java.util.Map;

/** Retrieval tool: finds the passages of the course materials most relevant to a query. */
public class SearchMaterialsTool implements StudyTool {

    private final SearchIndex index;

    public SearchMaterialsTool(SearchIndex index) {
        this.index = index;
    }

    @Override
    public String getName() {
        return "search_materials";
    }

    @Override
    public String getDescription() {
        return "Search the student's uploaded course materials and return the most relevant passages with their chunk ids.";
    }

    @Override
    public String getParameters() {
        return "{\"query\": \"what to look for\", \"k\": 1-8 (optional, default 4)}";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        Integer k = ToolArgs.integer(args, "k", 4);
        return ToolArgs.hasText(args, "query") && k != null && k >= 1 && k <= 8;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        List<Chunk> found = index.search(ToolArgs.text(args, "courseId"), ToolArgs.text(args, "query"),
                ToolArgs.integer(args, "k", 4));
        if (found.isEmpty()) {
            return ToolResult.ok("No passages in the materials match that query.");
        }
        StringBuilder sb = new StringBuilder();
        for (Chunk c : found) {
            String text = c.getText();
            sb.append('[').append(c.getId()).append("] (").append(c.reference()).append(") ")
                    .append(text.length() > 600 ? text.substring(0, 600) + "..." : text).append('\n');
        }
        return ToolResult.ok(sb.toString());
    }
}
