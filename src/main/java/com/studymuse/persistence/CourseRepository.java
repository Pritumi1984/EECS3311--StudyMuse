package com.studymuse.persistence;

import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Material;
import com.studymuse.domain.QuizResult;

import java.util.List;
import java.util.Optional;

/** Storage abstraction for courses. Services depend on this interface, not on the JSON files (dependency inversion). */
public interface CourseRepository {

    void saveCourse(Course course);

    Optional<Course> findCourse(String id);

    List<Course> findAllCourses();

    void saveMaterial(String courseId, Material material);

    void saveDeck(String courseId, Deck deck);

    void saveQuizResult(String courseId, QuizResult result);

    List<QuizResult> findQuizResults(String courseId);
}
