package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.services.DuplicateMaterialException;
import com.studymuse.services.StudyException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line interface. Run with no command for an interactive "studymuse>" prompt, or pass one
 * command directly, e.g.  run.bat cli upload eecs3311 sample-data\design-patterns.txt
 * Every command only parses input and calls the StudyAgentController facade.
 */
public class StudyCLI implements StudyView {

    private final StudyAgentController controller;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public StudyCLI(StudyAgentController controller) {
        this.controller = controller;
    }

    public void run(String[] args) {
        if (args.length > 0) {
            dispatch(Arrays.asList(args));
            return;
        }
        showResult("StudyMuse CLI - AI provider: " + controller.getLlmName() + "\nType 'help' for commands, 'exit' to quit.");
        while (true) {
            System.out.print("\nstudymuse> ");
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                return;
            }
            if (line == null) {
                return;
            }
            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) {
                continue;
            }
            if (tokens.get(0).equalsIgnoreCase("exit") || tokens.get(0).equalsIgnoreCase("quit")) {
                return;
            }
            dispatch(tokens);
        }
    }

    private void dispatch(List<String> t) {
        String cmd = t.get(0).toLowerCase();
        try {
            switch (cmd) {
                case "help" -> showResult(help());
                case "courses" -> listCourses();
                case "course" -> {
                    need(t, 3, "course create <name>");
                    if (!t.get(1).equalsIgnoreCase("create")) {
                        throw new StudyException("Usage: course create <name>");
                    }
                    Course c = controller.createCourse(String.join(" ", t.subList(2, t.size())));
                    showResult("Created course " + c);
                }
                case "materials" -> {
                    need(t, 2, "materials <courseId>");
                    listMaterials(t.get(1));
                }
                case "upload" -> {
                    need(t, 3, "upload <courseId> <file> [--replace]");
                    boolean replace = t.contains("--replace");
                    upload(t.get(1), t.get(2), replace);
                }
                default -> showError("Unknown command '" + cmd + "'. Type 'help'.");
            }
        } catch (StudyException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError("Unexpected error: " + e);
        }
    }

    private void listCourses() {
        List<Course> courses = controller.listCourses();
        if (courses.isEmpty()) {
            showResult("No courses yet. Create one: course create EECS3311");
            return;
        }
        StringBuilder sb = new StringBuilder("Courses:\n");
        for (Course c : courses) {
            sb.append("  ").append(c).append("  - ").append(c.getMaterials().size()).append(" materials\n");
        }
        showResult(sb.toString());
    }

    private void listMaterials(String courseId) {
        Course c = controller.getCourse(courseId);
        if (c.getMaterials().isEmpty()) {
            showResult("No materials in " + c.getName() + ". Upload one: upload " + c.getId() + " <file>");
            return;
        }
        StringBuilder sb = new StringBuilder("Materials in " + c.getName() + ":\n");
        for (Material m : c.getMaterials()) {
            sb.append("  ").append(m.getId()).append("  ").append(m).append('\n');
        }
        showResult(sb.toString());
    }

    private void upload(String courseId, String file, boolean replace) {
        try {
            report(controller.uploadMaterial(courseId, Paths.get(file), replace));
        } catch (DuplicateMaterialException e) {
            if (confirm(e.getMessage() + " Replace it? (y/n) ")) {
                report(controller.uploadMaterial(courseId, Paths.get(file), true));
            } else {
                showResult("Upload cancelled.");
            }
        }
    }

    private void report(Material m) {
        if (!m.hasText()) {
            showResult("Warning: no text found in " + m.getFileName() + " (empty or scanned file). Saved without chunks.");
        } else {
            showResult("Uploaded " + m + "\nMaterial id: " + m.getId());
        }
    }

    private boolean confirm(String question) {
        System.out.print(question);
        try {
            String a = in.readLine();
            return a != null && a.trim().toLowerCase().startsWith("y");
        } catch (IOException e) {
            return false;
        }
    }

    private static void need(List<String> t, int n, String usage) {
        if (t.size() < n) {
            throw new StudyException("Usage: " + usage);
        }
    }

    static String help() {
        return """
                Commands:
                  courses                              list courses
                  course create <name>                 create a course (F01)
                  upload <courseId> <file> [--replace] import a .txt/.md file (F01)
                  materials <courseId>                 list a course's materials
                  help | exit""";
    }

    /** Splits a command line on spaces, keeping "quoted text" together. */
    static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    @Override
    public void showResult(String text) {
        System.out.println(text);
    }

    @Override
    public void showError(String message) {
        System.out.println("Error: " + message);
    }
}
