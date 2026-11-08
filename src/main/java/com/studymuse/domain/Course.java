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
    private final List<Quiz> quizzes = new ArrayList<>();
    private final List<QuizResult> quizResults = new ArrayList<>();
    private final List<WeakTopic> weakTopics = new ArrayList<>();
    private StudyPlan studyPlan;
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final Map<String, String> agentFacts = new LinkedHashMap<>();

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
    public List<Quiz> getQuizzes() { return List.copyOf(quizzes); }
    public void addQuiz(Quiz q) { quizzes.add(q); }
    public List<QuizResult> getQuizResults() { return List.copyOf(quizResults); }
    public void addQuizResult(QuizResult r) { quizResults.add(r); }
    public List<WeakTopic> getWeakTopics() { return List.copyOf(weakTopics); }

    public List<ChatMessage> getChatHistory() { return List.copyOf(chatHistory); }
    public Map<String, String> getAgentFacts() { return Map.copyOf(agentFacts); }

    /** Keeps only the newest maxMessages turns. */
    public void addChatMessage(ChatMessage m, int maxMessages) {
        chatHistory.add(m);
        while (chatHistory.size() > maxMessages) {
            chatHistory.remove(0);
        }
    }

    public void putAgentFact(String key, String value) { agentFacts.put(key, value); }

    public StudyPlan getStudyPlan() { return studyPlan; }
    public void setStudyPlan(StudyPlan plan) { this.studyPlan = plan; }

    /** Latest weak-topic analysis, kept for the study planner (F10). */
    public void setWeakTopics(List<WeakTopic> latest) {
        weakTopics.clear();
        weakTopics.addAll(latest);
    }

    public Optional<Quiz> findQuiz(String quizId) {
        return quizzes.stream().filter(q -> q.getId().equalsIgnoreCase(quizId)).findFirst();
    }

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
        List<Object> qz = new ArrayList<>();
        for (Quiz q : quizzes) {
            qz.add(q.toMap());
        }
        m.put("quizzes", qz);
        List<Object> qr = new ArrayList<>();
        for (QuizResult r : quizResults) {
            qr.add(r.toMap());
        }
        m.put("quizResults", qr);
        List<Object> wt = new ArrayList<>();
        for (WeakTopic w : weakTopics) {
            wt.add(w.toMap());
        }
        m.put("weakTopics", wt);
        m.put("studyPlan", studyPlan == null ? null : studyPlan.toMap());
        List<Object> ch = new ArrayList<>();
        for (ChatMessage cm : chatHistory) {
            ch.add(cm.toMap());
        }
        m.put("chatHistory", ch);
        m.put("agentFacts", new LinkedHashMap<>(agentFacts));
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
        for (Object o : Json.list(m, "quizzes")) {
            c.quizzes.add(Quiz.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "quizResults")) {
            c.quizResults.add(QuizResult.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "weakTopics")) {
            c.weakTopics.add(WeakTopic.fromMap(Json.obj(o)));
        }
        for (Object o : Json.list(m, "chatHistory")) {
            c.chatHistory.add(ChatMessage.fromMap(Json.obj(o)));
        }
        Json.obj(m.get("agentFacts")).forEach((k, v) -> c.agentFacts.put(k, String.valueOf(v)));
        if (m.get("studyPlan") != null) {
            c.studyPlan = StudyPlan.fromMap(Json.obj(m.get("studyPlan")));
        }
        return c;
    }
}
