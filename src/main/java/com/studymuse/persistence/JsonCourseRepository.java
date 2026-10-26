package com.studymuse.persistence;

import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.services.StudyException;
import com.studymuse.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Saves each course as one JSON file in DATA_DIR/courses on the student's computer. */
public class JsonCourseRepository implements CourseRepository {

    private final Path dir;

    public JsonCourseRepository(Path dataDir) {
        this.dir = dataDir.resolve("courses");
    }

    @Override
    public synchronized void saveCourse(Course course) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(course.getId() + ".json.tmp");
            Files.writeString(tmp, Json.stringify(course.toMap()), StandardCharsets.UTF_8);
            Files.move(tmp, fileFor(course.getId()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StudyException("Could not save course '" + course.getName() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized Optional<Course> findCourse(String id) {
        Path f = fileFor(id);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        return Optional.ofNullable(read(f));
    }

    @Override
    public synchronized List<Course> findAllCourses() {
        List<Course> courses = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return courses;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : files) {
                Course c = read(f);
                if (c != null) {
                    courses.add(c);
                }
            }
        } catch (IOException e) {
            throw new StudyException("Could not list courses: " + e.getMessage(), e);
        }
        courses.sort(Comparator.comparing(Course::getName, String.CASE_INSENSITIVE_ORDER));
        return courses;
    }

    @Override
    public synchronized void saveMaterial(String courseId, Material material) {
        Course course = findCourse(courseId).orElseThrow(() -> new StudyException("No course with id '" + courseId + "'."));
        course.putMaterial(material);
        saveCourse(course);
    }

    private Path fileFor(String id) {
        return dir.resolve(id + ".json");
    }

    /** Reads one course file; a corrupted file is skipped with a warning instead of crashing the app. */
    private Course read(Path f) {
        try {
            return Course.fromMap(Json.parseObject(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            System.err.println("Warning: skipping unreadable course file " + f.getFileName() + " (" + e.getMessage() + ")");
            return null;
        }
    }
}
