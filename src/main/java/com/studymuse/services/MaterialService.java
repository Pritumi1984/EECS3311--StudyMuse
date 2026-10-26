package com.studymuse.services;

import com.studymuse.documents.DocumentReader;
import com.studymuse.documents.DocumentReaderFactory;
import com.studymuse.documents.SearchIndex;
import com.studymuse.documents.TextChunker;
import com.studymuse.domain.Chunk;
import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.persistence.CourseRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** F01: creates courses and imports study files (read -> chunk -> index -> save). Fully deterministic. */
public class MaterialService {

    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private final DocumentReaderFactory readerFactory;
    private final TextChunker chunker;
    private final SearchIndex index;
    private final CourseRepository repository;

    public MaterialService(DocumentReaderFactory readerFactory, TextChunker chunker, SearchIndex index,
                           CourseRepository repository) {
        this.readerFactory = readerFactory;
        this.chunker = chunker;
        this.index = index;
        this.repository = repository;
    }

    /** Loads every saved course into the search index (called once at start-up). */
    public void rebuildIndex() {
        for (Course c : repository.findAllCourses()) {
            for (Material m : c.getMaterials()) {
                index.add(c.getId(), m.getChunks());
            }
        }
    }

    public Course createCourse(String name) {
        if (name == null || name.isBlank()) {
            throw new StudyException("Course name cannot be empty.");
        }
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (id.isEmpty()) {
            throw new StudyException("Course name must contain letters or digits.");
        }
        if (repository.findCourse(id).isPresent()) {
            throw new StudyException("A course with id '" + id + "' already exists.");
        }
        Course course = new Course(id, name.trim());
        repository.saveCourse(course);
        return course;
    }

    public Course getCourse(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new StudyException("Please choose a course first.");
        }
        return repository.findCourse(courseId.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new StudyException("No course with id '" + courseId + "'. Use 'courses' to list them."));
    }

    public List<Course> listCourses() {
        return repository.findAllCourses();
    }

    public Material getMaterial(String courseId, String materialIdOrName) {
        Course course = getCourse(courseId);
        return course.findMaterial(materialIdOrName)
                .orElseThrow(() -> new StudyException("No material '" + materialIdOrName + "' in course " + course.getId() + "."));
    }

    /**
     * Imports a file. Throws DuplicateMaterialException when the name exists and replaceExisting is false,
     * so the UI can ask the student. A file with no extractable text is saved with zero chunks.
     */
    public Material upload(String courseId, Path file, boolean replaceExisting) {
        Course course = getCourse(courseId);
        if (file == null || !Files.isRegularFile(file)) {
            throw new StudyException("File not found: " + file);
        }
        String fileName = file.getFileName().toString();
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new StudyException("'" + fileName + "' is larger than 20 MB.");
            }
        } catch (IOException e) {
            throw new StudyException("Cannot read " + fileName + ": " + e.getMessage(), e);
        }
        DocumentReader reader = readerFactory.createReader(file);
        Material existing = course.findMaterial(fileName).orElse(null);
        if (existing != null && !replaceExisting) {
            throw new DuplicateMaterialException(fileName);
        }
        String text;
        try {
            text = reader.readText(file);
        } catch (IOException e) {
            throw new StudyException("Could not read text from " + fileName + ": " + e.getMessage(), e);
        }
        String materialId = existing != null ? existing.getId() : newMaterialId(fileName);
        List<Chunk> chunks = chunker.split(materialId, fileName, text);
        Material material = new Material(materialId, fileName,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), TextChunker.countWords(text), chunks);
        if (existing != null) {
            index.removeMaterial(course.getId(), materialId);
        }
        index.add(course.getId(), chunks);
        repository.saveMaterial(course.getId(), material);
        return material;
    }

    private static String newMaterialId(String fileName) {
        String base = fileName.replaceAll("\\.[^.]*$", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("(^-|-$)", "");
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        return (base.isEmpty() ? "doc" : base) + "-" + UUID.randomUUID().toString().substring(0, 4);
    }
}
