package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.documents.SearchIndex;
import com.studymuse.domain.Chunk;
import com.studymuse.domain.Course;
import com.studymuse.domain.GradeResult;
import com.studymuse.domain.Question;
import com.studymuse.domain.QuestionType;
import com.studymuse.domain.Quiz;
import com.studymuse.domain.QuizRequest;
import com.studymuse.domain.QuizResult;
import com.studymuse.grading.GradingStrategy;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.progress.ProgressTracker;
import com.studymuse.util.Json;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * F07 quiz generation (AI) and F08 grading (hybrid).
 * F07: Retrieves chunks for the chosen topics, asks the LLM for questions in a
 * strict JSON format, validates every question, regenerates the invalid/missing ones once, and saves
 * the quiz on the course.
 */
public class QuizService {

    public static final int MIN_QUESTIONS = 3;
    public static final int MAX_QUESTIONS = 30;
    static final String QUESTION_FORMAT = "{\"questions\": [{\"text\": \"...\", \"type\": \"MULTIPLE_CHOICE or SHORT_ANSWER\", "
            + "\"options\": [\"A\", \"B\", \"C\", \"D\"], \"correctAnswer\": \"exact text of the correct option, "
            + "or a model answer for SHORT_ANSWER\", \"topic\": \"one of the requested topics\"}]}";

    protected final StructuredLLMCall ai;
    protected final SearchIndex index;
    protected final CourseRepository repository;
    private final Map<QuestionType, GradingStrategy> graders;
    private final ProgressTracker tracker;

    /** graders: the STRATEGY for each question type (context = this service). */
    public QuizService(StructuredLLMCall ai, SearchIndex index, CourseRepository repository,
                       Map<QuestionType, GradingStrategy> graders, ProgressTracker tracker) {
        this.tracker = tracker;
        this.ai = ai;
        this.index = index;
        this.repository = repository;
        this.graders = new EnumMap<>(graders);
    }

    // ------------------------------------------------------------------ F08 grading

    /**
     * Grades every question with the strategy registered for its type (blank answers score 0 with no
     * AI call), asks the LLM to explain each mistake from the course material, and saves the attempt.
     */
    public QuizResult grade(Course course, Quiz quiz, Map<String, String> answers) {
        List<GradeResult> grades = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            String answer = answers.getOrDefault(q.getId(), "");
            answer = answer == null ? "" : answer.trim();
            if (answer.isEmpty()) {
                grades.add(new GradeResult(q.getId(), q.getTopic(), q.getText(), "", q.getCorrectAnswer(), 0,
                        "No answer given.", false));
                continue;
            }
            GradingStrategy grader = graders.get(q.getType());
            if (grader == null) {
                throw new StudyException("No grader is registered for " + q.getType() + " questions.");
            }
            grades.add(grader.grade(q, answer));
        }
        explainMistakes(course, grades);
        QuizResult result = new QuizResult("result-" + UUID.randomUUID().toString().substring(0, 6), quiz.getId(),
                quiz.getTitle(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), grades);
        repository.saveQuizResult(course.getId(), result);
        tracker.recordQuizResult(course.getId(), result);
        return result;
    }

    /** One LLM call explains all wrong/partial answers, grounded in retrieved chunks. Failure keeps the basic feedback. */
    private void explainMistakes(Course course, List<GradeResult> grades) {
        List<GradeResult> wrong = grades.stream().filter(g -> !g.isPending() && g.getScore() < 1.0).toList();
        if (wrong.isEmpty()) {
            return;
        }
        Set<Chunk> context = new LinkedHashSet<>();
        StringBuilder req = new StringBuilder("Explain briefly (2-3 sentences each) why each answer below is wrong or "
                + "incomplete and what the correct idea is, using the course material.\n");
        for (GradeResult g : wrong) {
            context.addAll(index.search(course.getId(), g.getQuestionText() + " " + g.getCorrectAnswer(), 2));
            req.append("\nQUESTION ID: ").append(g.getQuestionId())
                    .append("\nQUESTION: ").append(g.getQuestionText())
                    .append("\nCORRECT ANSWER: ").append(g.getCorrectAnswer())
                    .append("\nSTUDENT ANSWER: ").append(g.getStudentAnswer().isEmpty() ? "(blank)" : g.getStudentAnswer())
                    .append('\n');
        }
        try {
            Map<String, Object> json = ai.ask(new PromptBuilder()
                    .withSystemInstructions("You are StudyMuse, a patient tutor.")
                    .withContext(new ArrayList<>(context).subList(0, Math.min(context.size(), 8)))
                    .withOutputFormat("{\"explanations\": [{\"questionId\": \"...\", \"explanation\": \"...\"}]}")
                    .withUserRequest(req.toString())
                    .build());
            for (Object o : Json.list(json, "explanations")) {
                Map<String, Object> e = Json.obj(o);
                String id = Json.str(e, "questionId");
                String text = Json.str(e, "explanation");
                for (GradeResult g : wrong) {
                    if (g.getQuestionId().equals(id) && text != null && !text.isBlank()) {
                        String prefix = g.getFeedback() == null || g.getFeedback().isBlank() ? "" : g.getFeedback() + " ";
                        g.setFeedback(prefix + text.trim());
                    }
                }
            }
        } catch (StudyException e) {
            // explanations are optional; the grades themselves are already complete
        }
    }

    public Quiz generate(Course course, QuizRequest request) {
        if (request.count() < MIN_QUESTIONS || request.count() > MAX_QUESTIONS) {
            throw new StudyException("Number of questions must be between " + MIN_QUESTIONS + " and " + MAX_QUESTIONS + ".");
        }
        List<String> topics = request.topics().stream().map(String::trim).filter(t -> !t.isEmpty()).toList();
        if (topics.isEmpty()) {
            throw new StudyException("Enter at least one topic for the quiz.");
        }
        if (request.types().isEmpty()) {
            throw new StudyException("Choose at least one question type.");
        }
        // retrieval: every topic must appear in the student's materials
        Set<Chunk> context = new LinkedHashSet<>();
        for (String topic : topics) {
            List<Chunk> found = index.search(course.getId(), topic, 3);
            if (found.isEmpty()) {
                throw new StudyException("Topic '" + topic + "' was not found in your materials. Please choose another topic.");
            }
            context.addAll(found);
        }
        List<Chunk> chunks = new ArrayList<>(context).subList(0, Math.min(context.size(), 10));

        List<Question> questions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addValid(askForQuestions(chunks, request, topics, request.count(), List.of()), request, topics, questions, seen);
        if (questions.size() < request.count()) {
            // one regeneration attempt for the invalid or missing questions
            int missing = request.count() - questions.size();
            List<String> existing = questions.stream().map(Question::getText).toList();
            addValid(askForQuestions(chunks, request, topics, missing, existing), request, topics, questions, seen);
        }
        if (questions.isEmpty()) {
            throw new StudyException("The AI did not produce any valid questions. Please try again.");
        }
        if (questions.size() > request.count()) {
            questions = new ArrayList<>(questions.subList(0, request.count()));
        }
        String title = "Quiz on " + String.join(", ", topics);
        Quiz quiz = new Quiz("quiz-" + UUID.randomUUID().toString().substring(0, 6), title, request.difficulty(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), questions);
        course.addQuiz(quiz);
        repository.saveCourse(course);
        return quiz;
    }

    private Map<String, Object> askForQuestions(List<Chunk> chunks, QuizRequest request, List<String> topics, int n,
                                                List<String> avoid) {
        String types = request.types().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        StringBuilder req = new StringBuilder()
                .append("Create exactly ").append(n).append(" quiz questions.\n")
                .append("TOPICS: ").append(String.join(" | ", topics)).append('\n')
                .append("TYPES: ").append(types).append('\n')
                .append("DIFFICULTY: ").append(request.difficulty().name()).append('\n')
                .append("Rules: MULTIPLE_CHOICE questions have exactly 4 different options and correctAnswer is copied "
                        + "exactly from the options. SHORT_ANSWER questions have an empty options list and a 1-3 sentence "
                        + "model answer. Every question tests one of the TOPICS and is answerable from the material.");
        if (!avoid.isEmpty()) {
            req.append("\nDo not repeat these questions:\n- ").append(String.join("\n- ", avoid));
        }
        return ai.ask(new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, a study assistant that writes fair practice quizzes "
                        + "using only the course material given.")
                .withContext(chunks)
                .withOutputFormat(QUESTION_FORMAT)
                .withUserRequest(req.toString())
                .build());
    }

    /** Validates each question from the LLM and appends the good ones. */
    private void addValid(Map<String, Object> json, QuizRequest request, List<String> topics, List<Question> out,
                          Set<String> seen) {
        for (Object o : Json.list(json, "questions")) {
            Question q = validate(Json.obj(o), request, topics);
            if (q != null && seen.add(q.getText().toLowerCase(Locale.ROOT))) {
                out.add(q);
            }
        }
    }

    /** Returns a clean Question, or null when the LLM's question breaks a rule. */
    Question validate(Map<String, Object> m, QuizRequest request, List<String> topics) {
        String text = trim(Json.str(m, "text"));
        String answer = trim(Json.str(m, "correctAnswer"));
        if (text.isEmpty() || answer.isEmpty()) {
            return null;
        }
        QuestionType type;
        try {
            type = QuestionType.parse(Json.str(m, "type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!request.types().contains(type)) {
            return null;
        }
        List<String> options = new ArrayList<>();
        if (type == QuestionType.MULTIPLE_CHOICE) {
            for (Object opt : Json.list(m, "options")) {
                String s = trim(String.valueOf(opt));
                if (!s.isEmpty()) {
                    options.add(s);
                }
            }
            Set<String> distinct = options.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (options.size() != 4 || distinct.size() != 4) {
                return null;
            }
            String correct = matchOption(answer, options);
            if (correct == null) {
                return null;
            }
            answer = correct;
        }
        return new Question("q-" + UUID.randomUUID().toString().substring(0, 6), text, type, options, answer,
                normalizeTopic(trim(Json.str(m, "topic")), text, topics));
    }

    /** The correct answer must be one of the options (exact text, or a letter A-D). */
    private static String matchOption(String answer, List<String> options) {
        for (String o : options) {
            if (o.equalsIgnoreCase(answer)) {
                return o;
            }
        }
        String a = answer.trim().toUpperCase(Locale.ROOT);
        if (a.length() == 1 && a.charAt(0) >= 'A' && a.charAt(0) <= 'D') {
            return options.get(a.charAt(0) - 'A');
        }
        return null;
    }

    /** Maps the LLM's topic label onto one of the requested topics, so analytics groups answers correctly. */
    private static String normalizeTopic(String topic, String questionText, List<String> topics) {
        for (String t : topics) {
            if (t.equalsIgnoreCase(topic)) {
                return t;
            }
        }
        String hay = (topic + " " + questionText).toLowerCase(Locale.ROOT);
        for (String t : topics) {
            if (hay.contains(t.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return topics.get(0);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
