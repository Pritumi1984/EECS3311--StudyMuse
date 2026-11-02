package com.studymuse.grading;

import com.studymuse.domain.GradeResult;
import com.studymuse.domain.Question;

/**
 * Design pattern: STRATEGY (strategy interface).
 * Each question type is graded by its own algorithm. QuizService (the context) looks up the strategy
 * for a question's type, so adding a new question type means adding one class, not editing QuizService.
 */
public interface GradingStrategy {

    /** Grades a non-blank answer. */
    GradeResult grade(Question question, String answer);
}
