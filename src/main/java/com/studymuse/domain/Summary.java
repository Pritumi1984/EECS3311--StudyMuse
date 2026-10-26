package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI-generated summary of one material: an overview plus key points (F02). */
public class Summary {
    private final String overview;
    private final List<String> keyPoints;
    private final SummaryLength length;
    private final String createdAt;

    public Summary(String overview, List<String> keyPoints, SummaryLength length, String createdAt) {
        this.overview = overview;
        this.keyPoints = new ArrayList<>(keyPoints);
        this.length = length;
        this.createdAt = createdAt;
    }

    public String getOverview() { return overview; }
    public List<String> getKeyPoints() { return List.copyOf(keyPoints); }
    public SummaryLength getLength() { return length; }
    public String getCreatedAt() { return createdAt; }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Overview\n  ").append(overview).append("\n\nKey points\n");
        for (String p : keyPoints) {
            sb.append("  - ").append(p).append('\n');
        }
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overview", overview);
        m.put("keyPoints", keyPoints);
        m.put("length", length.name());
        m.put("createdAt", createdAt);
        return m;
    }

    public static Summary fromMap(Map<String, Object> m) {
        List<String> points = new ArrayList<>();
        for (Object o : Json.list(m, "keyPoints")) {
            points.add(String.valueOf(o));
        }
        return new Summary(Json.str(m, "overview"), points,
                SummaryLength.parse(Json.str(m, "length")), Json.str(m, "createdAt"));
    }
}
