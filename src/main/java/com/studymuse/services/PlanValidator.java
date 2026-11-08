package com.studymuse.services;

import com.studymuse.domain.StudyDay;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.StudySession;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic checks on a study plan (F10). Returns a list of human-readable problems; an empty list
 * means the plan is valid. The problems are sent back to the LLM so it can fix its draft.
 */
public class PlanValidator {

    public List<String> validate(StudyPlan plan, int dailyMinutes, LocalDate firstDay, LocalDate examDate,
                                 List<String> requiredTopics) {
        List<String> problems = new ArrayList<>();
        if (plan.getDays().isEmpty()) {
            problems.add("The plan has no days.");
        }
        Set<LocalDate> seenDates = new HashSet<>();
        Set<String> coveredTopics = new HashSet<>();
        for (StudyDay d : plan.getDays()) {
            LocalDate date = d.getDate();
            if (date.isBefore(firstDay) || !date.isBefore(examDate)) {
                problems.add(date + " is outside the allowed range " + firstDay + " to " + examDate.minusDays(1) + ".");
            }
            if (!seenDates.add(date)) {
                problems.add(date + " appears more than once; merge its sessions into one day.");
            }
            if (d.totalMinutes() > dailyMinutes) {
                problems.add(date + " has " + d.totalMinutes() + " minutes but only " + dailyMinutes + " are available.");
            }
            for (StudySession s : d.getSessions()) {
                if (s.getMinutes() <= 0) {
                    problems.add(date + " has a session with no time for " + s.getTopic() + ".");
                }
                coveredTopics.add(s.getTopic().trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String t : requiredTopics) {
            if (!coveredTopics.contains(t.trim().toLowerCase(Locale.ROOT))) {
                problems.add("Topic '" + t + "' is missing from the plan.");
            }
        }
        return problems;
    }
}
