package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A named set of flashcards generated from one material or concept (F05). */
public class Deck {
    private final String id;
    private final String name;
    private final String createdAt;
    private final List<Flashcard> cards;

    public Deck(String id, String name, String createdAt, List<Flashcard> cards) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.cards = new ArrayList<>(cards);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCreatedAt() { return createdAt; }
    public List<Flashcard> getCards() { return List.copyOf(cards); }

    public boolean removeCard(String cardId) {
        return cards.removeIf(c -> c.getId().equals(cardId));
    }

    @Override
    public String toString() {
        return name + "  (" + cards.size() + " cards)";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("createdAt", createdAt);
        List<Object> cs = new ArrayList<>();
        for (Flashcard c : cards) {
            cs.add(c.toMap());
        }
        m.put("cards", cs);
        return m;
    }

    public static Deck fromMap(Map<String, Object> m) {
        List<Flashcard> cs = new ArrayList<>();
        for (Object o : Json.list(m, "cards")) {
            cs.add(Flashcard.fromMap(Json.obj(o)));
        }
        return new Deck(Json.str(m, "id"), Json.str(m, "name"), Json.str(m, "createdAt"), cs);
    }
}
