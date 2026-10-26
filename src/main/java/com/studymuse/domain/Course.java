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

    public Course(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<Material> getMaterials() { return List.copyOf(materials); }
    public List<Concept> getConcepts() { return List.copyOf(concepts); }

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
        return c;
    }
}
