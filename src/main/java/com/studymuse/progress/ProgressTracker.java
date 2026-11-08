package com.studymuse.progress;

import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.QuizResult;
import com.studymuse.domain.Rating;
import com.studymuse.domain.StudyDay;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.StudySession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Design pattern: OBSERVER (subject).
 * QuizService, FlashcardService and the study planner report progress here; every subscribed
 * ProgressObserver is notified. getStats() computes the dashboard numbers from the saved course.
 */
public class ProgressTracker {

    static final int MASTERED_INTERVAL_DAYS = 21;
    static final int TREND_LENGTH = 10;

    private final List<ProgressObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(ProgressObserver o) {
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    public void removeObserver(ProgressObserver o) {
        observers.remove(o);
    }

    public void recordQuizResult(String courseId, QuizResult result) {
        notifyObservers(new ProgressEvent(courseId, ProgressEvent.Type.QUIZ_GRADED,
                String.format("Quiz graded: %.0f%% on %s", result.getScorePercent(), result.getQuizTitle())));
    }

    public void recordReview(String courseId, Flashcard card, Rating rating) {
        notifyObservers(new ProgressEvent(courseId, ProgressEvent.Type.CARD_REVIEWED,
                "Flashcard reviewed (" + rating.name().toLowerCase() + "): next review " + card.getNextReview()));
    }

    public void recordPlanSession(String courseId, StudySession session) {
        notifyObservers(new ProgressEvent(courseId, ProgressEvent.Type.PLAN_SESSION_UPDATED,
                "Study session " + (session.isDone() ? "completed" : "reopened") + ": " + session.getActivity().getLabel()
                        + " - " + session.getTopic()));
    }

    private void notifyObservers(ProgressEvent event) {
        for (ProgressObserver o : observers) {
            try {
                o.onProgressUpdated(event);
            } catch (RuntimeException e) {
                System.err.println("Warning: a progress observer failed: " + e.getMessage());
            }
        }
    }

    public ProgressStats getStats(Course course) {
        List<QuizResult> results = course.getQuizResults();
        List<Double> scores = new ArrayList<>();
        int questionsAnswered = 0;
        for (QuizResult r : results) {
            scores.add(r.getScorePercent());
            questionsAnswered += r.getGrades().size();
        }
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        List<Double> trend = scores.subList(Math.max(0, scores.size() - TREND_LENGTH), scores.size());

        int cards = 0;
        int mastered = 0;
        for (Deck d : course.getDecks()) {
            for (Flashcard c : d.getCards()) {
                cards++;
                if (c.getIntervalDays() >= MASTERED_INTERVAL_DAYS) {
                    mastered++;
                }
            }
        }
        int reviews = course.getReviews().size();

        int planDone = 0;
        int planTotal = 0;
        int planMinutes = 0;
        StudyPlan plan = course.getStudyPlan();
        if (plan != null) {
            for (StudyDay day : plan.getDays()) {
                for (StudySession s : day.getSessions()) {
                    planTotal++;
                    if (s.isDone()) {
                        planDone++;
                        planMinutes += s.getMinutes();
                    }
                }
            }
        }
        int minutes = planMinutes + reviews + 2 * questionsAnswered;
        return new ProgressStats(course.getName(), course.getMaterials().size(), results.size(), average, trend, cards,
                mastered, reviews, minutes, planDone, planTotal);
    }
}
