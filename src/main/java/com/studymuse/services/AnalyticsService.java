package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.domain.Course;
import com.studymuse.domain.GradeResult;
import com.studymuse.domain.QuizResult;
import com.studymuse.domain.ReviewRecord;
import com.studymuse.domain.WeakTopic;
import com.studymuse.domain.WeakTopicReport;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F09 weak-topic identification (hybrid). Accuracy per topic is computed deterministically from quiz
 * grades and flashcard ratings; topics below 70% with at least 3 attempts are "weak"; the LLM then reads
 * the actual wrong answers and describes the pattern of mistakes. Results are saved for the study planner.
 */
public class AnalyticsService {

    public static final double WEAK_THRESHOLD = 0.70;
    public static final int MIN_ATTEMPTS = 3;
    static final int MAX_EXAMPLES = 5;

    /** Running statistics for one topic. */
    static final class TopicStats {
        final String name;
        int attempts;
        double scoreSum;
        final List<String> mistakes = new ArrayList<>();

        TopicStats(String name) {
            this.name = name;
        }

        double accuracy() {
            return attempts == 0 ? 0 : scoreSum / attempts;
        }
    }

    private final StructuredLLMCall ai;
    private final CourseRepository repository;

    public AnalyticsService(StructuredLLMCall ai, CourseRepository repository) {
        this.ai = ai;
        this.repository = repository;
    }

    public WeakTopicReport findWeakTopics(Course course) {
        Map<String, TopicStats> stats = computeTopicAccuracy(course.getQuizResults(), course.getReviews());
        List<WeakTopic> all = new ArrayList<>();
        for (TopicStats s : stats.values()) {
            all.add(new WeakTopic(s.name, s.accuracy(), s.attempts, null));
        }
        all.sort(Comparator.comparingDouble(WeakTopic::accuracy).thenComparing(WeakTopic::topic));

        List<WeakTopic> eligible = all.stream().filter(t -> t.attempts() >= MIN_ATTEMPTS).toList();
        if (eligible.isEmpty()) {
            return new WeakTopicReport(List.of(), all, "Not enough data yet: a topic needs at least " + MIN_ATTEMPTS
                    + " attempts (quiz answers or flashcard reviews). Take more quizzes first.");
        }
        List<WeakTopic> weak = eligible.stream().filter(t -> t.accuracy() < WEAK_THRESHOLD).toList();
        if (weak.isEmpty()) {
            WeakTopic lowest = eligible.get(0);
            course.setWeakTopics(List.of());
            repository.saveCourse(course);
            return new WeakTopicReport(List.of(), all, String.format("Great work! No topic is below %d%%. "
                    + "Your lowest topic is %s at %d%%.", (int) (WEAK_THRESHOLD * 100), lowest.topic(), lowest.accuracyPercent()));
        }
        List<WeakTopic> diagnosed = diagnose(weak, stats);
        course.setWeakTopics(diagnosed);
        repository.saveCourse(course);
        return new WeakTopicReport(diagnosed, all, diagnosed.size() + " weak topic(s) found (below "
                + (int) (WEAK_THRESHOLD * 100) + "% with at least " + MIN_ATTEMPTS + " attempts), weakest first:");
    }

    /**
     * Deterministic part: quiz answers count with their score (0, 0.5, 1; pending answers are skipped)
     * and flashcard reviews with their rating (Again 0, Hard 0.5, Good/Easy 1). Topics are matched case-insensitively.
     */
    Map<String, TopicStats> computeTopicAccuracy(List<QuizResult> attempts, List<ReviewRecord> reviews) {
        Map<String, TopicStats> stats = new LinkedHashMap<>();
        for (QuizResult r : attempts) {
            for (GradeResult g : r.getGrades()) {
                if (g.isPending() || g.getTopic() == null) {
                    continue;
                }
                TopicStats s = stats.computeIfAbsent(key(g.getTopic()), k -> new TopicStats(g.getTopic().trim()));
                s.attempts++;
                s.scoreSum += g.getScore();
                if (g.getScore() < 1.0) {
                    s.mistakes.add("Q: " + g.getQuestionText() + " | student: "
                            + (g.getStudentAnswer().isBlank() ? "(blank)" : g.getStudentAnswer()) + " | correct: " + g.getCorrectAnswer());
                }
            }
        }
        for (ReviewRecord rv : reviews) {
            if (rv.topic() == null) {
                continue;
            }
            TopicStats s = stats.computeIfAbsent(key(rv.topic()), k -> new TopicStats(rv.topic().trim()));
            s.attempts++;
            s.scoreSum += rv.rating().getCorrectness();
        }
        return stats;
    }

    /** AI part: one call diagnoses every weak topic from its real mistakes. Falls back to a generic note on failure. */
    private List<WeakTopic> diagnose(List<WeakTopic> weak, Map<String, TopicStats> stats) {
        StringBuilder req = new StringBuilder("For each weak topic, describe in 1-2 sentences the pattern in the student's "
                + "mistakes (what they misunderstand) and one concrete thing to review.\n");
        for (WeakTopic w : weak) {
            TopicStats s = stats.get(key(w.topic()));
            req.append("\nTOPIC: ").append(w.topic()).append(" (").append(w.accuracyPercent()).append("% accuracy)\n");
            List<String> examples = s.mistakes.subList(0, Math.min(MAX_EXAMPLES, s.mistakes.size()));
            if (examples.isEmpty()) {
                req.append("- (mistakes come from flashcard reviews rated Again/Hard; no written answers)\n");
            }
            for (String m : examples) {
                req.append("- ").append(m).append('\n');
            }
        }
        Map<String, String> byTopic = new LinkedHashMap<>();
        String fallback = "Accuracy is low on this topic; review its notes and flashcards, then retake a quiz.";
        try {
            Map<String, Object> json = ai.ask(new PromptBuilder()
                    .withSystemInstructions("You are StudyMuse, a supportive tutor analysing a student's quiz mistakes.")
                    .withOutputFormat("{\"diagnoses\": [{\"topic\": \"...\", \"diagnosis\": \"...\"}]}")
                    .withUserRequest(req.toString())
                    .build());
            for (Object o : Json.list(json, "diagnoses")) {
                Map<String, Object> d = Json.obj(o);
                String t = Json.str(d, "topic");
                String text = Json.str(d, "diagnosis");
                if (t != null && text != null && !text.isBlank()) {
                    byTopic.put(key(t), text.trim());
                }
            }
        } catch (StudyException e) {
            fallback = "(AI diagnosis unavailable: " + e.getMessage() + ") " + fallback;
        }
        List<WeakTopic> out = new ArrayList<>();
        for (WeakTopic w : weak) {
            out.add(w.withDiagnosis(byTopic.getOrDefault(key(w.topic()), fallback)));
        }
        return out;
    }

    private static String key(String topic) {
        return topic.trim().toLowerCase(Locale.ROOT);
    }
}
