package com.studymuse.domain;

import java.util.List;
import java.util.Set;

/** What the student asks for when creating a quiz (F07). */
public record QuizRequest(List<String> topics, int count, Set<QuestionType> types, Difficulty difficulty) {

    public QuizRequest {
        topics = List.copyOf(topics);
        types = Set.copyOf(types);
    }
}
