package com.studymuse.tools;

import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.GradeResult;
import com.studymuse.domain.QuizResult;
import com.studymuse.services.FlashcardService;
import com.studymuse.services.MaterialService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the wrong answers of the student's most recent quiz attempt into flashcards (deterministic):
 * front = the question, back = the correct answer. Lets the agent "add my mistakes to my flashcards".
 */
public class MistakesToFlashcardsTool implements StudyTool {

    private final FlashcardService flashcards;
    private final MaterialService materials;

    public MistakesToFlashcardsTool(FlashcardService flashcards, MaterialService materials) {
        this.flashcards = flashcards;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "flashcards_from_mistakes";
    }

    @Override
    public String getDescription() {
        return "Create a flashcard deck from the wrong or partial answers of the student's latest graded quiz attempt.";
    }

    @Override
    public String getParameters() {
        return "{} (no arguments)";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Course course = materials.getCourse(ToolArgs.text(args, "courseId"));
        List<QuizResult> results = course.getQuizResults();
        if (results.isEmpty()) {
            return ToolResult.fail("The student has not taken any quiz yet, so there are no mistakes to turn into cards.");
        }
        QuizResult latest = results.get(results.size() - 1);
        List<Flashcard> cards = new ArrayList<>();
        for (GradeResult g : latest.getGrades()) {
            if (!g.isPending() && g.getScore() < 1.0) {
                cards.add(new Flashcard("card-" + UUID.randomUUID().toString().substring(0, 8), g.getQuestionText(),
                        g.getCorrectAnswer(), g.getTopic()));
            }
        }
        if (cards.isEmpty()) {
            return ToolResult.ok("The latest quiz attempt (" + latest.getQuizTitle() + ") had no mistakes - nothing to add.");
        }
        Deck deck = new Deck("deck-" + UUID.randomUUID().toString().substring(0, 6), "Mistakes: " + latest.getQuizTitle(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), cards);
        flashcards.saveDeck(course.getId(), deck);
        return ToolResult.ok("Saved deck '" + deck.getName() + "' with " + cards.size() + " card(s) from the mistakes.");
    }
}
