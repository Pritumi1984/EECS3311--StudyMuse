package com.studymuse.grading;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.domain.GradeResult;
import com.studymuse.domain.Question;
import com.studymuse.services.StudyException;
import com.studymuse.util.Json;

import java.util.Map;

/**
 * Concrete STRATEGY: the LLM compares the student's short answer with the model answer using a fixed
 * rubric and returns 0, 0.5 or 1 with a reason. Any other score is rejected. If the AI service is
 * unavailable the answer is marked pending instead of failing the whole quiz.
 */
public class ShortAnswerGrader implements GradingStrategy {

    private final StructuredLLMCall ai;

    public ShortAnswerGrader(StructuredLLMCall ai) {
        this.ai = ai;
    }

    @Override
    public GradeResult grade(Question q, String answer) {
        try {
            Map<String, Object> json = ai.ask(new PromptBuilder()
                    .withSystemInstructions("You are a fair teaching assistant grading a student's short answer. Rubric: "
                            + "1 = captures the key idea of the model answer (wording may differ); 0.5 = partly correct or "
                            + "missing an important part; 0 = wrong, irrelevant or empty. Be concise.")
                    .withOutputFormat("{\"score\": 0 or 0.5 or 1, \"reason\": \"one sentence\"}")
                    .withUserRequest("QUESTION: " + q.getText() + "\nMODEL ANSWER: " + q.getCorrectAnswer()
                            + "\nSTUDENT ANSWER: " + answer)
                    .build());
            double score = Json.decimal(json, "score", -1);
            if (score != 0 && score != 0.5 && score != 1) {
                return GradeResult.pending(q, answer, "The AI returned an invalid score; grade this answer later.");
            }
            String reason = Json.str(json, "reason");
            return new GradeResult(q.getId(), q.getTopic(), q.getText(), answer, q.getCorrectAnswer(), score,
                    reason == null ? "" : reason.trim(), false);
        } catch (StudyException e) {
            return GradeResult.pending(q, answer, "Not graded yet: " + e.getMessage());
        }
    }
}
