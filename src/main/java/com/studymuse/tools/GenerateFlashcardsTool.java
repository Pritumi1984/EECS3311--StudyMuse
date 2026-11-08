package com.studymuse.tools;

import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.services.FlashcardService;
import com.studymuse.services.MaterialService;

import java.util.Map;

/** Generates and saves a flashcard deck (F05) from a material or concept, because the student asked the agent to. */
public class GenerateFlashcardsTool implements StudyTool {

    private final FlashcardService flashcards;
    private final MaterialService materials;

    public GenerateFlashcardsTool(FlashcardService flashcards, MaterialService materials) {
        this.flashcards = flashcards;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "generate_flashcards";
    }

    @Override
    public String getDescription() {
        return "Generate and save a flashcard deck from a material file name or a concept/topic name.";
    }

    @Override
    public String getParameters() {
        return "{\"source\": \"file name or topic\", \"count\": " + FlashcardService.MIN_CARDS + "-" + FlashcardService.MAX_CARDS + "}";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        Integer count = ToolArgs.integer(args, "count", 10);
        return ToolArgs.hasText(args, "source") && count != null
                && count >= FlashcardService.MIN_CARDS && count <= FlashcardService.MAX_CARDS;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Course course = materials.getCourse(ToolArgs.text(args, "courseId"));
        Deck deck = flashcards.generate(course, ToolArgs.text(args, "source"), ToolArgs.integer(args, "count", 10));
        flashcards.saveDeck(course.getId(), deck);
        return ToolResult.ok("Saved deck '" + deck.getName() + "' with " + deck.getCards().size()
                + " cards (id " + deck.getId() + "). The student can review them in the Review tab.");
    }
}
