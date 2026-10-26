package com.studymuse.services;

/** Thrown when a file with the same name is already in the course; the UI asks whether to replace it. */
public class DuplicateMaterialException extends StudyException {
    private static final long serialVersionUID = 1L;

    public DuplicateMaterialException(String fileName) {
        super("A material named '" + fileName + "' already exists in this course.");
    }
}
