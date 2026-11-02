package com.studymuse.grading;

import com.studymuse.domain.GradeResult;
import com.studymuse.domain.Question;

import java.util.List;
import java.util.Locale;

/** Concrete STRATEGY: exact, deterministic check against the answer key. Accepts the option text or its letter A-D. */
public class MultipleChoiceGrader implements GradingStrategy {

    @Override
    public GradeResult grade(Question q, String answer) {
        String chosen = resolve(answer, q.getOptions());
        boolean correct = chosen.equalsIgnoreCase(q.getCorrectAnswer());
        return new GradeResult(q.getId(), q.getTopic(), q.getText(), chosen, q.getCorrectAnswer(), correct ? 1.0 : 0.0,
                correct ? "Correct." : "", false);
    }

    /** Turns "B" or "b)" into the text of option B; any other input is kept as typed. */
    static String resolve(String answer, List<String> options) {
        String a = answer.trim();
        String letter = a.replaceAll("[).:\\s]+$", "").toUpperCase(Locale.ROOT);
        if (letter.length() == 1 && letter.charAt(0) >= 'A' && letter.charAt(0) < 'A' + options.size()) {
            return options.get(letter.charAt(0) - 'A');
        }
        return a;
    }
}
