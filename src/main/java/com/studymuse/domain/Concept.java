package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** A key term from the materials with a one-sentence definition (F04). Concepts are the course's topics. */
public class Concept {
    private final String name;
    private final String definition;
    private final String sourceChunkId;

    public Concept(String name, String definition, String sourceChunkId) {
        this.name = name;
        this.definition = definition;
        this.sourceChunkId = sourceChunkId;
    }

    public String getName() { return name; }
    public String getDefinition() { return definition; }
    public String getSourceChunkId() { return sourceChunkId; }

    @Override
    public String toString() {
        return name + " - " + definition;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("definition", definition);
        m.put("sourceChunkId", sourceChunkId);
        return m;
    }

    public static Concept fromMap(Map<String, Object> m) {
        return new Concept(Json.str(m, "name"), Json.str(m, "definition"), Json.str(m, "sourceChunkId"));
    }
}
