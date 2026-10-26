package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** A numbered piece (about 500 words) of a material's text. Chunks are what retrieval searches. */
public class Chunk {
    private final String id;
    private final String materialId;
    private final String sourceName;
    private final int index;
    private final String text;

    public Chunk(String id, String materialId, String sourceName, int index, String text) {
        this.id = id;
        this.materialId = materialId;
        this.sourceName = sourceName;
        this.index = index;
        this.text = text;
    }

    public String getId() { return id; }
    public String getMaterialId() { return materialId; }
    public String getSourceName() { return sourceName; }
    public int getIndex() { return index; }
    public String getText() { return text; }

    /** Human-readable reference such as "notes.txt, chunk 3". */
    public String reference() {
        return sourceName + ", chunk " + (index + 1);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("materialId", materialId);
        m.put("sourceName", sourceName);
        m.put("index", index);
        m.put("text", text);
        return m;
    }

    public static Chunk fromMap(Map<String, Object> m) {
        return new Chunk(Json.str(m, "id"), Json.str(m, "materialId"), Json.str(m, "sourceName"),
                Json.integer(m, "index", 0), Json.str(m, "text"));
    }
}
