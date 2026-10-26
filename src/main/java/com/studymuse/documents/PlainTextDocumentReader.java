package com.studymuse.documents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Reads .txt and .md files. Markdown symbols are kept; they do not disturb retrieval. */
public class PlainTextDocumentReader implements DocumentReader {

    private static final Set<String> EXTENSIONS = Set.of("txt", "md", "markdown");

    @Override
    public boolean supports(String extension) {
        return EXTENSIONS.contains(extension.toLowerCase());
    }

    @Override
    public String readText(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
