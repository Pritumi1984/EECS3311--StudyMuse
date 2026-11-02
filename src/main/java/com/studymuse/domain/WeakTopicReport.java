package com.studymuse.domain;

import java.util.List;

/**
 * Result of weak-topic analysis (F09): the ranked weak topics, every topic's statistics, and a message
 * for the student (e.g. "not enough data yet" or "no topic below 70%").
 */
public record WeakTopicReport(List<WeakTopic> weakTopics, List<WeakTopic> allTopics, String message) {

    public WeakTopicReport {
        weakTopics = List.copyOf(weakTopics);
        allTopics = List.copyOf(allTopics);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder(message).append("\n");
        int rank = 1;
        for (WeakTopic w : weakTopics) {
            sb.append(String.format("%n%d. %s - %d%% accuracy over %d attempts%n   Diagnosis: %s%n", rank++, w.topic(),
                    w.accuracyPercent(), w.attempts(), w.diagnosis()));
        }
        if (!allTopics.isEmpty()) {
            sb.append("\nAll topics:\n");
            for (WeakTopic t : allTopics) {
                sb.append(String.format("  %-28s %3d%%  (%d attempts)%n", t.topic(), t.accuracyPercent(), t.attempts()));
            }
        }
        return sb.toString();
    }
}
