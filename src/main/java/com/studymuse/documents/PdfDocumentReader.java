package com.studymuse.documents;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads .pdf files. Design pattern: ADAPTER - it adapts Apache PDFBox (PDDocument + PDFTextStripper)
 * to our DocumentReader interface, so the rest of StudyMuse never sees PDFBox classes.
 *
 * PDFBox is an optional library: run.sh / run.bat download it into lib/ (git-ignored). It is called
 * through reflection so the project still compiles and runs with the plain JDK; without the library
 * this reader is simply not registered and the app explains how to enable PDF support.
 * Works with PDFBox 3.x (Loader.loadPDF) and 2.x (PDDocument.load).
 */
public class PdfDocumentReader implements DocumentReader {

    static final String STRIPPER = "org.apache.pdfbox.text.PDFTextStripper";
    static final String DOCUMENT = "org.apache.pdfbox.pdmodel.PDDocument";
    static final String LOADER_3X = "org.apache.pdfbox.Loader";

    /** Kept in a field so the setting is not garbage-collected: hides PDFBox's font-cache warnings. */
    private static final Logger PDFBOX_LOG = Logger.getLogger("org.apache.pdfbox");

    static {
        PDFBOX_LOG.setLevel(Level.SEVERE);
    }

    /** True when a compatible PDFBox is on the classpath. */
    public static boolean isAvailable() {
        try {
            Class.forName(STRIPPER);
            Class.forName(DOCUMENT);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public String readText(Path file) throws IOException {
        Object document = null;
        try {
            document = load(file.toFile());
            Object stripper = Class.forName(STRIPPER).getConstructor().newInstance();
            Method getText = stripper.getClass().getMethod("getText", Class.forName(DOCUMENT));
            String text = (String) getText.invoke(stripper, document);
            if (text == null || text.isBlank()) {
                throw new IOException("the PDF has no selectable text (it may be a scanned image). "
                        + "Try a text-based PDF or copy the text into a .txt file.");
            }
            return text;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw new IOException("PDF could not be read (" + io.getMessage() + "). It may be damaged or password-protected.", io);
            }
            throw new IOException("PDF could not be read: " + cause, cause);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IOException("PDF support is not set up correctly: " + e, e);
        } finally {
            close(document);
        }
    }

    private static Object load(File file) throws ReflectiveOperationException {
        try {
            Method loadPdf = Class.forName(LOADER_3X).getMethod("loadPDF", File.class); // PDFBox 3.x
            return loadPdf.invoke(null, file);
        } catch (ClassNotFoundException e) {
            Method load = Class.forName(DOCUMENT).getMethod("load", File.class); // PDFBox 2.x
            return load.invoke(null, file);
        }
    }

    private static void close(Object document) {
        if (document instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nothing useful to do if closing fails
            }
        }
    }
}
