package com.studymuse.documents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Design pattern: FACTORY METHOD.
 * createReader(file) chooses the right DocumentReader from the file extension, so MaterialService
 * never contains if/else on file types. Supporting a new format means registering one new reader.
 */
public class DocumentReaderFactory {

    private final List<DocumentReader> readers = new ArrayList<>();

    public DocumentReaderFactory() {
        readers.add(new PlainTextDocumentReader());
    }

    public void register(DocumentReader reader) {
        readers.add(reader);
    }

    public DocumentReader createReader(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        for (DocumentReader r : readers) {
            if (r.supports(ext)) {
                return r;
            }
        }
        throw new UnsupportedFileException(name, supportedTypes());
    }

    public String supportedTypes() {
        List<String> exts = new ArrayList<>();
        for (String e : List.of("txt", "md", "pdf", "docx")) {
            for (DocumentReader r : readers) {
                if (r.supports(e)) {
                    exts.add("." + e);
                    break;
                }
            }
        }
        return String.join(", ", exts);
    }
}
