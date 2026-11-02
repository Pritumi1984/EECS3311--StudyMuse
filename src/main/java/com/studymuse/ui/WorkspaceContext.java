package com.studymuse.ui;

import com.studymuse.domain.Course;
import com.studymuse.domain.Material;

import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * What a feature panel needs from the main window: the current selection, a way to run slow
 * AI work in the background, and status/error reporting. Keeps each feature panel independent.
 */
interface WorkspaceContext {

    Course selectedCourse();

    Material selectedMaterial();

    /** Runs work off the Swing thread, then calls onDone with the result (or shows the error and calls onError). */
    <T> void runInBackground(String busyText, Callable<T> work, Consumer<T> onDone, Runnable onError);

    void showError(String message);

    void setStatus(String text);

    /** Reloads courses from storage, keeping the current selection. */
    void reloadCourses();

    Component window();
}
