package com.studymuse.documents;

import java.io.IOException;
import java.nio.file.Path;

/** Design pattern: product interface of the FACTORY METHOD; target interface for reader ADAPTERs. */
public interface DocumentReader {

    boolean supports(String extension);

    String readText(Path file) throws IOException;
}
