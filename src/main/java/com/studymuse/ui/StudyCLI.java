package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Answer;
import com.studymuse.domain.Concept;
import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Difficulty;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Material;
import com.studymuse.domain.PlanRequest;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.Question;
import com.studymuse.domain.QuestionType;
import com.studymuse.domain.Quiz;
import com.studymuse.domain.QuizRequest;
import com.studymuse.domain.QuizResult;
import com.studymuse.domain.Rating;
import com.studymuse.domain.Summary;
import com.studymuse.domain.SummaryLength;
import com.studymuse.services.DuplicateMaterialException;
import com.studymuse.services.StudyException;
import com.studymuse.progress.ProgressEvent;
import com.studymuse.progress.ProgressObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Command-line interface. Run with no command for an interactive "studymuse>" prompt, or pass one
 * command directly, e.g.  run.bat cli upload eecs3311 sample-data\design-patterns.txt
 * Every command only parses input and calls the StudyAgentController facade.
 */
public final class StudyCLI implements StudyView, ProgressObserver {

    private final StudyAgentController controller;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public StudyCLI(StudyAgentController controller) {
        this.controller = controller;
        controller.addProgressObserver(this);
    }

    /** OBSERVER: the CLI prints a one-line notice whenever progress changes. */
    @Override
    public void onProgressUpdated(ProgressEvent event) {
        System.out.println("  [progress] " + event.description());
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
                case "summarize" -> {
                    need(t, 3, "summarize <courseId> <material> [short|medium|detailed]");
                    SummaryLength len = SummaryLength.parse(t.size() > 3 ? t.get(3) : "medium");
                    showResult("Summarizing with " + controller.getLlmName() + " ...");
                    Summary s = controller.summarize(t.get(1), t.get(2), len);
                    showResult(s.toText());
                }
                case "ask" -> {
                    need(t, 3, "ask <courseId> <question>");
                    String question = String.join(" ", t.subList(2, t.size()));
                    showResult("Searching your materials and asking " + controller.getLlmName() + " ...");
                    Answer a = controller.askQuestion(t.get(1), question);
                    showResult(a.toText());
                }
                case "concepts" -> {
                    need(t, 3, "concepts <courseId> <material>");
                    showResult("Extracting key concepts with " + controller.getLlmName() + " ...");
                    StringBuilder sb = new StringBuilder("Key concepts:\n");
                    for (Concept c : controller.extractConcepts(t.get(1), t.get(2))) {
                        sb.append("  * ").append(c.getName()).append(": ").append(c.getDefinition()).append('\n');
                    }
                    showResult(sb.toString());
                }
                case "flashcards" -> {
                    need(t, 2, "flashcards generate <courseId> <material|concept> [count]");
                    if (t.get(1).equalsIgnoreCase("generate")) {
                        need(t, 4, "flashcards generate <courseId> <material|concept> [count]");
                        int count = t.size() > 4 ? parseInt(t.get(4), "count") : 10;
                        generateFlashcards(t.get(2), t.get(3), count);
                    } else if (t.get(1).equalsIgnoreCase("review")) {
                        need(t, 3, "flashcards review <courseId>");
                        reviewFlashcards(t.get(2));
                    } else {
                        throw new StudyException("Usage: flashcards generate <courseId> <material|concept> [count]"
                                + " | flashcards review <courseId>");
                    }
                }
                case "quiz" -> {
                    need(t, 3, "quiz generate <courseId> --topics \"A,B\" [--count 10] [--difficulty medium] [--types mc,sa]");
                    if (t.get(1).equalsIgnoreCase("generate")) {
                        generateQuiz(t);
                    } else if (t.get(1).equalsIgnoreCase("take")) {
                        need(t, 4, "quiz take <courseId> <quizId>");
                        takeQuiz(t.get(2), t.get(3));
                    } else {
                        throw new StudyException("Usage: quiz generate <courseId> --topics \"A,B\" ...");
                    }
                }
                case "quizzes" -> {
                    need(t, 2, "quizzes <courseId>");
                    List<Quiz> quizzes = controller.listQuizzes(t.get(1));
                    if (quizzes.isEmpty()) {
                        showResult("No quizzes yet. Try: quiz generate " + t.get(1) + " --topics \"Observer\" --count 5");
                    } else {
                        StringBuilder sb = new StringBuilder("Quizzes:\n");
                        for (Quiz q : quizzes) {
                            sb.append("  ").append(q.getId()).append("  ").append(q).append('\n');
                        }
                        showResult(sb.toString());
                    }
                }
                case "plan" -> {
                    need(t, 2, "plan <courseId> --exam YYYY-MM-DD --hours 2 [--topics \"A,B\"]  |  plan <courseId> show");
                    if (t.size() > 4 && t.get(2).equalsIgnoreCase("done")) {
                        LocalDate date = LocalDate.parse(t.get(3));
                        int n = parseInt(t.get(4), "session number");
                        controller.setPlanSessionDone(t.get(1), date, n - 1, true);
                        showResult("Marked session " + n + " on " + date + " as done.");
                    } else if (t.size() > 2 && t.get(2).equalsIgnoreCase("show")) {
                        showResult(controller.getPlan(t.get(1)).map(StudyPlan::toText)
                                .orElse("No study plan yet. Create one: plan " + t.get(1) + " --exam YYYY-MM-DD --hours 2"));
                    } else {
                        makePlan(t);
                    }
                }
                case "progress" -> {
                    need(t, 2, "progress <courseId>");
                    showResult(controller.getProgress(t.get(1)).toText());
                }
                case "weak-topics" -> {
                    need(t, 2, "weak-topics <courseId>");
                    showResult("Analysing your quiz and flashcard history ...");
                    showResult(controller.getWeakTopics(t.get(1)).toText());
                }
                case "decks" -> {
                    need(t, 2, "decks <courseId>");
                    List<Deck> decks = controller.listDecks(t.get(1));
                    if (decks.isEmpty()) {
                        showResult("No flashcard decks yet. Try: flashcards generate " + t.get(1) + " <material> 10");
                    } else {
                        StringBuilder sb = new StringBuilder("Decks:\n");
                        for (Deck d : decks) {
                            sb.append("  ").append(d.getId()).append("  ").append(d).append('\n');
                        }
                        showResult(sb.toString());
                    }
                }
                default -> showError("Unknown command '" + cmd + "'. Type 'help'.");
            }
        } catch (StudyException | IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError("Unexpected error: " + e);
        }
    }

    /** F05: generate a preview deck, let the student drop bad cards, then save. */
    private void generateFlashcards(String courseId, String source, int count) {
        showResult("Generating " + count + " flashcards with " + controller.getLlmName() + " ...");
        Deck deck = controller.generateFlashcards(courseId, source, count);
        printDeck(deck);
        if (deck.getCards().size() < count) {
            showResult("Note: only " + deck.getCards().size() + " valid cards were created (asked for " + count + ").");
        }
        System.out.print("Numbers of cards to delete (e.g. 2 5), Enter to keep all, or 'cancel': ");
        String reply;
        try {
            reply = in.readLine();
        } catch (IOException e) {
            reply = "cancel";
        }
        if (reply == null || reply.trim().equalsIgnoreCase("cancel")) {
            showResult("Deck discarded. Nothing was saved.");
            return;
        }
        List<Flashcard> cards = deck.getCards();
        for (String part : reply.trim().split("[\\s,]+")) {
            if (part.isBlank()) {
                continue;
            }
            int n = parseInt(part, "card number");
            if (n < 1 || n > cards.size()) {
                throw new StudyException("There is no card " + n + ".");
            }
            deck.removeCard(cards.get(n - 1).getId());
        }
        Deck saved = controller.saveDeck(courseId, deck);
        showResult("Saved deck " + saved + " (id " + saved.getId() + ").");
    }

    /** F07: parse options, generate and save a quiz, then preview its questions (answers hidden). */
    private void generateQuiz(List<String> t) {
        String courseId = t.get(2);
        String topics = option(t, "--topics", null);
        if (topics == null) {
            throw new StudyException("Give the quiz topics, e.g. --topics \"UML,Observer\"");
        }
        int count = parseInt(option(t, "--count", "10"), "count");
        Difficulty difficulty = Difficulty.parse(option(t, "--difficulty", "medium"));
        EnumSet<QuestionType> types = EnumSet.noneOf(QuestionType.class);
        for (String ty : option(t, "--types", "mc,sa").split(",")) {
            types.add(QuestionType.parse(ty));
        }
        showResult("Generating a " + difficulty.name().toLowerCase() + " quiz with " + controller.getLlmName() + " ...");
        Quiz quiz = controller.generateQuiz(courseId,
                new QuizRequest(Arrays.asList(topics.split(",")), count, types, difficulty));
        StringBuilder sb = new StringBuilder("Created " + quiz + "  (id " + quiz.getId() + ")\n");
        int i = 1;
        for (Question q : quiz.getQuestions()) {
            sb.append(String.format("%n%2d. [%s, %s] %s%n", i++, q.getTopic(),
                    q.getType() == QuestionType.MULTIPLE_CHOICE ? "multiple choice" : "short answer", q.getText()));
            char letter = 'A';
            for (String o : q.getOptions()) {
                sb.append("      ").append(letter++).append(") ").append(o).append('\n');
            }
        }
        if (quiz.getQuestions().size() < count) {
            sb.append("\nNote: only ").append(quiz.getQuestions().size()).append(" valid questions were created (asked for ")
                    .append(count).append(").");
        }
        showResult(sb.toString());
    }

    /** F08: ask every question, collect answers (A-D or free text, Enter = blank), then grade. */
    private void takeQuiz(String courseId, String quizId) {
        Quiz quiz = controller.getQuiz(courseId, quizId);
        showResult(quiz + "\nMultiple choice: type A-D. Short answer: type your answer. Enter skips a question.");
        Map<String, String> answers = new LinkedHashMap<>();
        int i = 1;
        for (Question q : quiz.getQuestions()) {
            System.out.printf("%n%d. %s%n", i++, q.getText());
            char letter = 'A';
            for (String o : q.getOptions()) {
                System.out.println("   " + letter++ + ") " + o);
            }
            System.out.print("   Your answer: ");
            String a = readLine();
            if (a == null) {
                break;
            }
            if (q.getType() == QuestionType.MULTIPLE_CHOICE && !a.isBlank()
                    && !a.trim().toUpperCase().matches("[A-" + (char) ('A' + q.getOptions().size() - 1) + "][).]?")) {
                showResult("   (Not a valid letter - counted as your typed answer.)");
            }
            answers.put(q.getId(), a);
        }
        showResult("\nGrading with " + controller.getLlmName() + " ...");
        QuizResult result = controller.submitQuiz(courseId, quizId, answers);
        showResult(result.toText());
    }

    /** F10: parse exam date, hours and optional topics, then build and print the plan. */
    private void makePlan(List<String> t) {
        String exam = option(t, "--exam", null);
        if (exam == null) {
            throw new StudyException("Give the exam date, e.g. --exam 2026-11-20");
        }
        LocalDate examDate;
        try {
            examDate = LocalDate.parse(exam);
        } catch (java.time.format.DateTimeParseException e) {
            throw new StudyException("Exam date must look like 2026-11-20.");
        }
        double hours;
        try {
            hours = Double.parseDouble(option(t, "--hours", "2"));
        } catch (NumberFormatException e) {
            throw new StudyException("Hours must be a number, e.g. --hours 1.5");
        }
        String topics = option(t, "--topics", "");
        showResult("Finding weak topics and planning with " + controller.getLlmName() + " ...");
        StudyPlan plan = controller.generatePlan(t.get(1),
                new PlanRequest(examDate, hours, topics.isBlank() ? List.of() : Arrays.asList(topics.split(","))));
        showResult(plan.toText());
    }

    /** Value after a --flag, or the default when the flag is absent. */
    private static String option(List<String> t, String flag, String def) {
        int i = t.indexOf(flag);
        if (i < 0) {
            return def;
        }
        if (i + 1 >= t.size()) {
            throw new StudyException("Missing value after " + flag + ".");
        }
        return t.get(i + 1);
    }

    /** F06: show each due card, flip on Enter, rate 1-4; ratings are saved one by one. */
    private void reviewFlashcards(String courseId) {
        List<Flashcard> due = controller.getDueCards(courseId, LocalDate.now());
        if (due.isEmpty()) {
            showResult(controller.nextDueDate(courseId)
                    .map(d -> "No cards are due today. Next review: " + d + ".")
                    .orElse("No flashcards yet. Create some with: flashcards generate " + courseId + " <material> 10"));
            return;
        }
        showResult(due.size() + " card(s) due. Press Enter to flip, then rate 1=Again 2=Hard 3=Good 4=Easy (q = stop).");
        int reviewed = 0;
        int remembered = 0;
        for (Flashcard card : due) {
            System.out.print("\n[" + card.getTopic() + "] " + card.getFront() + "\n  (Enter to flip) ");
            String line = readLine();
            if (line == null || line.trim().equalsIgnoreCase("q")) {
                break;
            }
            Rating rating = null;
            while (rating == null) {
                System.out.print("  Answer: " + card.getBack() + "\n  Rate 1-4: ");
                line = readLine();
                if (line == null || line.trim().equalsIgnoreCase("q")) {
                    break;
                }
                try {
                    rating = Rating.parse(line);
                } catch (IllegalArgumentException e) {
                    showError(e.getMessage());
                }
            }
            if (rating == null) {
                break;
            }
            Flashcard updated = controller.reviewCard(courseId, card.getId(), rating);
            reviewed++;
            if (rating.getCorrectness() >= 1.0) {
                remembered++;
            }
            showResult("  Next review in " + updated.getIntervalDays() + " day(s), on " + updated.getNextReview() + ".");
        }
        if (reviewed > 0) {
            showResult(String.format("%nSession: %d card(s) reviewed, %d%% remembered (Good/Easy). Ratings are saved.",
                    reviewed, Math.round(100.0 * remembered / reviewed)));
        }
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private void printDeck(Deck deck) {
        StringBuilder sb = new StringBuilder("Preview: " + deck.getName() + "\n");
        int i = 1;
        for (Flashcard c : deck.getCards()) {
            sb.append(String.format("%3d. [%s] %s%n      -> %s%n", i++, c.getTopic(), c.getFront(), c.getBack()));
        }
        showResult(sb.toString());
    }

    private static int parseInt(String text, String what) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new StudyException("'" + text + "' is not a valid " + what + ".");
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
                  summarize <courseId> <material> [short|medium|detailed]   AI summary (F02)
                  ask <courseId> <question>            answer from your materials with sources (F03)
                  concepts <courseId> <material>       extract key concepts with definitions (F04)
                  flashcards generate <courseId> <material|concept> [count]   AI flashcards, 5-50 (F05)
                  decks <courseId>                     list saved flashcard decks
                  flashcards review <courseId>         review due cards with spaced repetition (F06)
                  quiz generate <courseId> --topics "A,B" [--count 3-30] [--difficulty easy|medium|hard] [--types mc,sa]   (F07)
                  quizzes <courseId>                   list saved quizzes
                  quiz take <courseId> <quizId>        answer a quiz and get graded feedback (F08)
                  weak-topics <courseId>               find weak topics and why you miss them (F09)
                  plan <courseId> --exam YYYY-MM-DD --hours 2 [--topics "A,B"]   day-by-day study plan (F10)
                  plan <courseId> show                 show the saved plan
                  plan <courseId> done <YYYY-MM-DD> <n>   mark session n of that day as done
                  progress <courseId>                  progress summary (F11)
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
