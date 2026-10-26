package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An uploaded study file (F01). Owns its chunks and, once generated, its summary. */
public class Material {
    private final String id;
    private final String fileName;
    private final String uploadedAt;
    private final int wordCount;
    private final List<Chunk> chunks;
    private Summary summary;

    public Material(String id, String fileName, String uploadedAt, int wordCount, List<Chunk> chunks) {
        this.id = id;
        this.fileName = fileName;
        this.uploadedAt = uploadedAt;
        this.wordCount = wordCount;
        this.chunks = new ArrayList<>(chunks);
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public String getUploadedAt() { return uploadedAt; }
    public int getWordCount() { return wordCount; }
    public List<Chunk> getChunks() { return List.copyOf(chunks); }
    public boolean hasText() { return !chunks.isEmpty(); }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    @Override
    public String toString() {
        return fileName + "  (" + chunks.size() + " chunks, " + wordCount + " words)";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("fileName", fileName);
        m.put("uploadedAt", uploadedAt);
        m.put("wordCount", wordCount);
        List<Object> cs = new ArrayList<>();
        for (Chunk c : chunks) {
            cs.add(c.toMap());
        }
        m.put("chunks", cs);
        m.put("summary", summary == null ? null : summary.toMap());
        return m;
    }

    public static Material fromMap(Map<String, Object> m) {
        List<Chunk> cs = new ArrayList<>();
        for (Object o : Json.list(m, "chunks")) {
            cs.add(Chunk.fromMap(Json.obj(o)));
        }
        Material mat = new Material(Json.str(m, "id"), Json.str(m, "fileName"), Json.str(m, "uploadedAt"),
                Json.integer(m, "wordCount", 0), cs);
        if (m.get("summary") != null) {
            mat.setSummary(Summary.fromMap(Json.obj(m.get("summary"))));
        }
        return mat;
    }
}
