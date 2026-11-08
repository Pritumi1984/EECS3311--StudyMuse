package com.studymuse.domain;

import com.studymuse.util.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A day-by-day schedule up to an exam (F10). "source" records whether the AI or the fallback built it. */
public class StudyPlan {
    private final String examDate;
    private final double dailyHours;
    private final String createdAt;
    private final List<StudyDay> days;
    private final List<String> omittedTopics;
    private final String source;

    public StudyPlan(LocalDate examDate, double dailyHours, String createdAt, List<StudyDay> days,
                     List<String> omittedTopics, String source) {
        this.examDate = examDate.toString();
        this.dailyHours = dailyHours;
        this.createdAt = createdAt;
        this.days = new ArrayList<>(days);
        this.omittedTopics = new ArrayList<>(omittedTopics);
        this.source = source;
    }

    public LocalDate getExamDate() { return LocalDate.parse(examDate); }
    public double getDailyHours() { return dailyHours; }
    public String getCreatedAt() { return createdAt; }
    public List<StudyDay> getDays() { return List.copyOf(days); }
    public List<String> getOmittedTopics() { return List.copyOf(omittedTopics); }
    public String getSource() { return source; }

    public int totalMinutes() {
        return days.stream().mapToInt(StudyDay::totalMinutes).sum();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder(String.format("Study plan until exam on %s (%s h/day, %d min total, %s)%n",
                examDate, trimNumber(dailyHours), totalMinutes(), source));
        for (StudyDay d : days) {
            sb.append(String.format("%n%s (%s) - %d min%n", d.getDate(), d.getDate().getDayOfWeek().toString().substring(0, 3),
                    d.totalMinutes()));
            for (StudySession s : d.getSessions()) {
                sb.append(String.format("   [%s] %3d min  %-20s %s%n", s.isDone() ? "x" : " ", s.getMinutes(),
                        s.getActivity().getLabel(), s.getTopic()));
            }
        }
        if (!omittedTopics.isEmpty()) {
            sb.append("\nNot enough time for: ").append(String.join(", ", omittedTopics))
                    .append(" (weak topics were prioritized).\n");
        }
        return sb.toString();
    }

    private static String trimNumber(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examDate", examDate);
        m.put("dailyHours", dailyHours);
        m.put("createdAt", createdAt);
        List<Object> ds = new ArrayList<>();
        for (StudyDay d : days) {
            ds.add(d.toMap());
        }
        m.put("days", ds);
        m.put("omittedTopics", omittedTopics);
        m.put("source", source);
        return m;
    }

    public static StudyPlan fromMap(Map<String, Object> m) {
        List<StudyDay> ds = new ArrayList<>();
        for (Object o : Json.list(m, "days")) {
            ds.add(StudyDay.fromMap(Json.obj(o)));
        }
        List<String> omitted = new ArrayList<>();
        for (Object o : Json.list(m, "omittedTopics")) {
            omitted.add(String.valueOf(o));
        }
        return new StudyPlan(LocalDate.parse(Json.str(m, "examDate")), Json.decimal(m, "dailyHours", 1),
                Json.str(m, "createdAt"), ds, omitted, Json.str(m, "source"));
    }
}
