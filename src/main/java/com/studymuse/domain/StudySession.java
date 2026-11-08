package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** One block of study time in a plan day (F10). "done" is ticked off by the student (F11 plan completion). */
public class StudySession {
    private final String topic;
    private final ActivityType activity;
    private final int minutes;
    private boolean done;

    public StudySession(String topic, ActivityType activity, int minutes, boolean done) {
        this.topic = topic;
        this.activity = activity;
        this.minutes = minutes;
        this.done = done;
    }

    public String getTopic() { return topic; }
    public ActivityType getActivity() { return activity; }
    public int getMinutes() { return minutes; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("topic", topic);
        m.put("activity", activity.name());
        m.put("minutes", minutes);
        m.put("done", done);
        return m;
    }

    public static StudySession fromMap(Map<String, Object> m) {
        return new StudySession(Json.str(m, "topic"), ActivityType.valueOf(Json.str(m, "activity")),
                Json.integer(m, "minutes", 0), Json.bool(m, "done", false));
    }
}
