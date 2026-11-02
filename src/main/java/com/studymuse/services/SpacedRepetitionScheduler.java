package com.studymuse.services;

import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Rating;

import java.time.LocalDate;

/**
 * F06 scheduling based on the SM-2 algorithm (deterministic, no AI).
 * Forgotten cards (Again) restart at a 1-day interval; remembered cards get longer intervals
 * (1 day, 6 days, then interval x ease factor). The ease factor drops for hard cards
 * (never below 1.3), so difficult cards come back sooner.
 */
public class SpacedRepetitionScheduler {

    static final double MIN_EASE = 1.3;

    public LocalDate nextReview(Flashcard card, Rating rating, LocalDate today) {
        int q = rating.getQuality();
        double ease = card.getEaseFactor() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        ease = Math.max(MIN_EASE, ease);
        int repetitions;
        int interval;
        if (q < 3) {
            repetitions = 0;
            interval = 1;
        } else {
            repetitions = card.getRepetitions() + 1;
            if (repetitions == 1) {
                interval = 1;
            } else if (repetitions == 2) {
                interval = 6;
            } else {
                interval = (int) Math.round(Math.max(1, card.getIntervalDays()) * ease);
            }
            if (rating == Rating.HARD) {
                interval = Math.max(1, (int) Math.round(interval * 0.8));
            } else if (rating == Rating.EASY) {
                interval = (int) Math.round(interval * 1.3) + (repetitions == 1 ? 2 : 0);
            }
        }
        LocalDate next = today.plusDays(interval);
        card.updateSchedule(interval, repetitions, Math.round(ease * 100) / 100.0, next);
        return next;
    }
}
