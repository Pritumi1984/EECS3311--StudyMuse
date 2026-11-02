package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.documents.SearchIndex;
import com.studymuse.domain.Chunk;
import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Material;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.util.Json;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F05 flashcard generation (AI). Cards come back as a preview Deck that the student can edit or
 * trim before saveDeck() stores it, so a bad AI run never silently enters the course.
 */
public class FlashcardService {

    public static final int MIN_CARDS = 5;
    public static final int MAX_CARDS = 50;
    static final String CARD_FORMAT = "{\"cards\": [{\"front\": \"question\", \"back\": \"answer\", \"topic\": \"concept\"}]}";

    private final StructuredLLMCall ai;
    private final SearchIndex index;
    private final CourseRepository repository;

    public FlashcardService(StructuredLLMCall ai, SearchIndex index, CourseRepository repository) {
        this.ai = ai;
        this.index = index;
        this.repository = repository;
    }

    /**
     * Generates a preview deck from a material (id or file name) or, if no material matches,
     * from a concept/topic name using retrieval. Nothing is saved yet.
     */
    public Deck generate(Course course, String source, int count) {
        if (count < MIN_CARDS || count > MAX_CARDS) {
            throw new StudyException("Number of cards must be between " + MIN_CARDS + " and " + MAX_CARDS + ".");
        }
        if (source == null || source.isBlank()) {
            throw new StudyException("Choose a material or type a concept to make flashcards from.");
        }
        List<Chunk> context;
        String deckName;
        Material material = course.findMaterial(source.trim()).orElse(null);
        if (material != null) {
            if (!material.hasText()) {
                throw new StudyException("'" + material.getFileName() + "' has no text to make flashcards from.");
            }
            List<Chunk> all = material.getChunks();
            context = all.subList(0, Math.min(all.size(), 8));
            deckName = material.getFileName();
        } else {
            context = index.search(course.getId(), source, 5);
            if (context.isEmpty()) {
                throw new StudyException("No material or concept named '" + source + "' was found in " + course.getName() + ".");
            }
            deckName = source.trim();
        }
        Map<String, Object> json = ai.ask(new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, a study assistant. Write flashcards that test understanding, "
                        + "using only the course material given. Each card has a short question on the front, a concise "
                        + "answer on the back, and the concept it belongs to as its topic. Do not repeat cards.")
                .withContext(context)
                .withOutputFormat(CARD_FORMAT)
                .withUserRequest("Create exactly " + count + " flashcards.")
                .build());
        List<Flashcard> cards = new ArrayList<>();
        Set<String> seenFronts = new HashSet<>();
        for (Object o : Json.list(json, "cards")) {
            Map<String, Object> c = Json.obj(o);
            String front = clean(Json.str(c, "front"));
            String back = clean(Json.str(c, "back"));
            String topic = clean(Json.str(c, "topic"));
            if (front.isEmpty() || back.isEmpty()) {
                continue; // invalid card: both sides are required
            }
            if (!seenFronts.add(front.toLowerCase(Locale.ROOT))) {
                continue; // duplicate question
            }
            cards.add(new Flashcard("card-" + UUID.randomUUID().toString().substring(0, 8), front, back,
                    topic.isEmpty() ? deckName : topic));
            if (cards.size() == count) {
                break;
            }
        }
        if (cards.isEmpty()) {
            throw new StudyException("The AI did not return any valid flashcards. Please try again.");
        }
        return new Deck("deck-" + UUID.randomUUID().toString().substring(0, 6), deckName,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), cards);
    }

    /** Stores a reviewed preview deck on the course. */
    public Deck saveDeck(String courseId, Deck deck) {
        if (deck.getCards().isEmpty()) {
            throw new StudyException("The deck has no cards left to save.");
        }
        for (Flashcard c : deck.getCards()) {
            if (c.getFront().isBlank() || c.getBack().isBlank()) {
                throw new StudyException("Every card needs a front and a back.");
            }
        }
        repository.saveDeck(courseId, deck);
        return deck;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
