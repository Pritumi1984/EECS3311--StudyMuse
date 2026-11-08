package com.studymuse.progress;

import java.util.List;

/**
 * Dashboard numbers for one course (F11). Minutes studied is an estimate: completed plan sessions plus
 * 1 minute per flashcard review and 2 minutes per quiz question answered.
 */
public record ProgressStats(String courseName, int materials, int quizzesTaken, double averageScore,
                            List<Double> scoreTrend, int cardsTotal, int cardsMastered, int reviewsDone,
                            int minutesStudied, int planSessionsDone, int planSessionsTotal) {

    public ProgressStats {
        scoreTrend = List.copyOf(scoreTrend);
    }

    public boolean hasActivity() {
        return quizzesTaken > 0 || reviewsDone > 0 || planSessionsDone > 0;
    }

    public String toText() {
        if (!hasActivity()) {
            return "No study activity yet for " + courseName + ". A good first step: generate flashcards or a quiz "
                    + "from one of your materials.";
        }
        StringBuilder sb = new StringBuilder("Progress for " + courseName + "\n");
        sb.append(String.format("  Quizzes taken:        %d%n", quizzesTaken));
        sb.append(String.format("  Average quiz score:   %.0f%%%n", averageScore));
        if (!scoreTrend.isEmpty()) {
            sb.append("  Score trend (oldest -> newest): ");
            for (Double s : scoreTrend) {
                sb.append(String.format("%.0f%% ", s));
            }
            sb.append('\n');
        }
        sb.append(String.format("  Flashcards mastered:  %d of %d (%d reviews)%n", cardsMastered, cardsTotal, reviewsDone));
        sb.append(String.format("  Minutes studied:      about %d%n", minutesStudied));
        if (planSessionsTotal > 0) {
            sb.append(String.format("  Study plan:           %d of %d sessions done (%d%%)%n", planSessionsDone, planSessionsTotal,
                    Math.round(100.0 * planSessionsDone / planSessionsTotal)));
        }
        return sb.toString();
    }
}
