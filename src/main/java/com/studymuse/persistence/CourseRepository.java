package com.studymuse.persistence;

import com.studymuse.domain.Course;
import com.studymuse.domain.Material;

import java.util.List;
import java.util.Optional;

/** Storage abstraction for courses. Services depend on this interface, not on the JSON files (dependency inversion). */
public interface CourseRepository {

    void saveCourse(Course course);

    Optional<Course> findCourse(String id);

    List<Course> findAllCourses();

    void saveMaterial(String courseId, Material material);
}
