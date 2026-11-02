package com.studymuse.domain;

import com.studymuse.util.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A question/answer card (F05) with its spaced-repetition state (F06):
 * interval in days, ease factor, successful repetitions in a row and the next review date.
 */
public class Flashcard {
    private final String id;
    private String front;
    private String back;
    private String topic;
    private int intervalDays;
    private int repetitions;
    private double easeFactor;
    private String nextReview;

    public Flashcard(String id, String front, String back, String topic) {
        this(id, front, back, topic, 0, 0, 2.5, LocalDate.now().toString());
    }

    public Flashcard(String id, String front, String back, String topic, int intervalDays, int repetitions,
                     double easeFactor, String nextReview) {
        this.id = id;
        this.front = front;
        this.back = back;
        this.topic = topic;
        this.intervalDays = intervalDays;
        this.repetitions = repetitions;
        this.easeFactor = easeFactor;
        this.nextReview = nextReview;
    }

    public String getId() { return id; }
    public String getFront() { return front; }
    public String getBack() { return back; }
    public String getTopic() { return topic; }
    public int getIntervalDays() { return intervalDays; }
    public int getRepetitions() { return repetitions; }
    public double getEaseFactor() { return easeFactor; }
    public LocalDate getNextReview() { return LocalDate.parse(nextReview); }

    /** Lets the student fix AI output in the preview before the deck is saved. */
    public void edit(String newFront, String newBack, String newTopic) {
        this.front = newFront;
        this.back = newBack;
        this.topic = newTopic;
    }

    /** Called by the spaced-repetition scheduler after a review. */
    public void updateSchedule(int newInterval, int newRepetitions, double newEase, LocalDate next) {
        this.intervalDays = newInterval;
        this.repetitions = newRepetitions;
        this.easeFactor = newEase;
        this.nextReview = next.toString();
    }

    public boolean isDue(LocalDate today) {
        return !getNextReview().isAfter(today);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("front", front);
        m.put("back", back);
        m.put("topic", topic);
        m.put("intervalDays", intervalDays);
        m.put("repetitions", repetitions);
        m.put("easeFactor", easeFactor);
        m.put("nextReview", nextReview);
        return m;
    }

    public static Flashcard fromMap(Map<String, Object> m) {
        return new Flashcard(Json.str(m, "id"), Json.str(m, "front"), Json.str(m, "back"), Json.str(m, "topic"),
                Json.integer(m, "intervalDays", 0), Json.integer(m, "repetitions", 0),
                Json.decimal(m, "easeFactor", 2.5), Json.str(m, "nextReview"));
    }
}
