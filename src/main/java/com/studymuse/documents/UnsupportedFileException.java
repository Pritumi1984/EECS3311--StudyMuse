package com.studymuse.documents;

import com.studymuse.services.StudyException;

/** The uploaded file type has no reader. */
public class UnsupportedFileException extends StudyException {
    private static final long serialVersionUID = 1L;

    public UnsupportedFileException(String fileName, String supported) {
        super("Cannot read '" + fileName + "'. Supported file types: " + supported + ".");
    }
}
