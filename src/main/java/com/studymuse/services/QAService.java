package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.ResponseFormatException;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.documents.SearchIndex;
import com.studymuse.domain.Answer;
import com.studymuse.domain.Chunk;

import java.util.List;
import java.util.Map;

/**
 * F03 question answering over materials (hybrid). Deterministic retrieval finds the 5 most relevant
 * chunks; the LLM must answer only from them and cite chunk ids; ResponseParser verifies the citations.
 */
public class QAService {

    static final int TOP_K = 5;
    static final String NOT_COVERED = "Your uploaded materials do not seem to cover this question, so I won't guess. "
            + "Try rephrasing with terms from your notes, or upload a material that covers it.";

    private final SearchIndex index;
    private final StructuredLLMCall ai;

    public QAService(SearchIndex index, StructuredLLMCall ai) {
        this.index = index;
        this.ai = ai;
    }

    public Answer answer(String courseId, String question) {
        if (question == null || question.isBlank()) {
            throw new StudyException("Please type a question.");
        }
        List<Chunk> chunks = index.search(courseId, question, TOP_K);
        if (chunks.isEmpty()) {
            return new Answer(NOT_COVERED, List.of(), false); // the LLM is never asked to guess
        }
        Map<String, Object> json = ai.ask(new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, a study assistant. Answer ONLY from the course material below. "
                        + "Cite the chunk ids you used. If the material does not contain the answer, say so and cite nothing.")
                .withContext(chunks)
                .withOutputFormat("{\"answer\": \"...\", \"citations\": [\"<chunk id>\"]}")
                .withUserRequest(question.trim())
                .build());
        try {
            return ai.parser().validateCitations(ai.parser().requireText(json, "answer"),
                    ai.parser().textList(json, "citations"), chunks);
        } catch (ResponseFormatException e) {
            throw new StudyException("The AI answer was incomplete: " + e.getMessage(), e);
        }
    }
}
