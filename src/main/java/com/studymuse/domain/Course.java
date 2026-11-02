package com.studymuse.domain;

import com.studymuse.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A course the student studies. It owns its materials and extracted concepts. */
public class Course {
    private final String id;
    private final String name;
    private final List<Material> materials = new ArrayList<>();
    private final List<Concept> concepts = new ArrayList<>();
    private final List<Deck> decks = new ArrayList<>();
    private final List<ReviewRecord> reviews = new ArrayList<>();

    public Course(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<Material> getMaterials() { return List.copyOf(materials); }
    public List<Concept> getConcepts() { return List.copyOf(concepts); }
    public List<Deck> getDecks() { return List.copyOf(decks); }

    public void addDeck(Deck deck) { decks.add(deck); }
    public List<ReviewRecord> getReviews() { return List.copyOf(reviews); }
    public void addReview(ReviewRecord r) { reviews.add(r); }

    /** Finds a flashcard in any deck of this course. */
    public Optional<Flashcard> findCard(String cardId) {
        for (Deck d : decks) {
            for (Flashcard c : d.getCards()) {
                if (c.getId().equals(cardId)) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    public void addMaterial(Material m) { materials.add(m); }
    public boolean removeMaterial(String materialId) { return materials.removeIf(m -> m.getId().equals(materialId)); }

    /** Replaces an existing material with the same id, or adds it. */
    public void putMaterial(Material m) {
        for (int i = 0; i < materials.size(); i++) {
            if (materials.get(i).getId().equals(m.getId())) {
                materials.set(i, m);
                return;
            }
        }
        materials.add(m);
    }

    /** Finds a material by id or by file name (case-insensitive). */
    public Optional<Material> findMaterial(String idOrName) {
        for (Material m : materials) {
            if (m.getId().equalsIgnoreCase(idOrName) || m.getFileName().equalsIgnoreCase(idOrName)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /** Adds concepts, merging duplicates by name (case-insensitive). */
    public void mergeConcepts(List<Concept> newConcepts) {
        for (Concept c : newConcepts) {
            boolean exists = concepts.stream().anyMatch(old -> old.getName().equalsIgnoreCase(c.getName()));
            if (!exists) {
                concepts.add(c);
            }
        }
    }

    @Override
    public String toString() {
        return name + "  [" + id + "]";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        List<Object> ms = new ArrayList<>();
        for (Material mat : materials) {
            ms.add(mat.toMap());
        }
        m.put("materials", ms);
        List<Object> cs = new ArrayList<>();
        for (Concept c : concepts) {
            cs.add(c.toMap());
        }
        m.put("concepts", cs);
        List<Object> ds = new ArrayList<>();
        for (Deck d : decks) {
            ds.add(d.toMap());
        }
        m.put("decks", ds);
        List<Object> rs = new ArrayList<>();
        for (ReviewRecord r : reviews) {
            rs.add(r.toMap());
        }
        m.put("reviews", rs);
        return m;
    }

    public static Course fromMap(Map<String, Object> m) {
        Course c = new Course(Json.str(m, "id"), Json.str(m, "name"));
        for (Object o : Json.list(m, "materials")) {
            c.addMaterial(Material.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "concepts")) {
            c.concepts.add(Concept.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "decks")) {
            c.decks.add(Deck.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "reviews")) {
            c.reviews.add(ReviewRecord.fromMap(Json.obj(o)));
        }
        return c;
    }
}
