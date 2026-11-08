package com.studymuse.domain;

import com.studymuse.util.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The sessions planned for one date (F10). */
public class StudyDay {
    private final String date;
    private final List<StudySession> sessions;

    public StudyDay(LocalDate date, List<StudySession> sessions) {
        this.date = date.toString();
        this.sessions = new ArrayList<>(sessions);
    }

    public LocalDate getDate() { return LocalDate.parse(date); }
    public List<StudySession> getSessions() { return List.copyOf(sessions); }

    public int totalMinutes() {
        return sessions.stream().mapToInt(StudySession::getMinutes).sum();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        List<Object> ss = new ArrayList<>();
        for (StudySession s : sessions) {
            ss.add(s.toMap());
        }
        m.put("sessions", ss);
        return m;
    }

    public static StudyDay fromMap(Map<String, Object> m) {
        List<StudySession> ss = new ArrayList<>();
        for (Object o : Json.list(m, "sessions")) {
            ss.add(StudySession.fromMap(Json.obj(o)));
        }
        return new StudyDay(LocalDate.parse(Json.str(m, "date")), ss);
    }
}
