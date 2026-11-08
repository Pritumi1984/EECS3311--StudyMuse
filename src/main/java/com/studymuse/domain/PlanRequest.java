package com.studymuse.domain;

import java.time.LocalDate;
import java.util.List;

/** What the student enters for a study plan (F10): exam date, hours per day and topics (empty = all course topics). */
public record PlanRequest(LocalDate examDate, double dailyHours, List<String> topics) {

    public PlanRequest {
        topics = topics == null ? List.of() : topics.stream().map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    public int dailyMinutes() {
        return (int) Math.round(dailyHours * 60);
    }
}
