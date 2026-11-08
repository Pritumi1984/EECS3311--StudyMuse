package com.studymuse.tools;

import com.studymuse.domain.Difficulty;
import com.studymuse.domain.Question;
import com.studymuse.domain.QuestionType;
import com.studymuse.domain.Quiz;
import com.studymuse.domain.QuizRequest;
import com.studymuse.services.MaterialService;
import com.studymuse.services.QuizService;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Creates and saves a practice quiz (F07) for the student to take in the Quiz tab or with "quiz take". */
public class GenerateQuizTool implements StudyTool {

    private final QuizService quizzes;
    private final MaterialService materials;

    public GenerateQuizTool(QuizService quizzes, MaterialService materials) {
        this.quizzes = quizzes;
        this.materials = materials;
    }

    @Override
    public String getName() {
        return "generate_quiz";
    }

    @Override
    public String getDescription() {
        return "Create and save a practice quiz on given topics. The student takes it in the Quiz tab (answers are not shown to you).";
    }

    @Override
    public String getParameters() {
        return "{\"topics\": [\"topic\"], \"count\": 3-30, \"difficulty\": \"easy|medium|hard\" (optional), "
                + "\"types\": \"mc,sa\" (optional)}";
    }

    @Override
    public boolean validateArgs(Map<String, Object> args) {
        Integer count = ToolArgs.integer(args, "count", 5);
        if (ToolArgs.list(args, "topics").isEmpty() || count == null) {
            return false;
        }
        try {
            Difficulty.parse(ToolArgs.hasText(args, "difficulty") ? ToolArgs.text(args, "difficulty") : "medium");
            for (String t : ToolArgs.list(args, "types")) {
                QuestionType.parse(t);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        EnumSet<QuestionType> types = EnumSet.noneOf(QuestionType.class);
        for (String t : ToolArgs.list(args, "types")) {
            types.add(QuestionType.parse(t));
        }
        if (types.isEmpty()) {
            types = EnumSet.allOf(QuestionType.class);
        }
        QuizRequest req = new QuizRequest(ToolArgs.list(args, "topics"), ToolArgs.integer(args, "count", 5), types,
                Difficulty.parse(ToolArgs.hasText(args, "difficulty") ? ToolArgs.text(args, "difficulty") : "medium"));
        Quiz quiz = quizzes.generate(materials.getCourse(ToolArgs.text(args, "courseId")), req);
        StringBuilder sb = new StringBuilder("Saved quiz '" + quiz.getTitle() + "' with id " + quiz.getId() + " ("
                + quiz.getQuestions().size() + " questions). Questions:\n");
        List<Question> qs = quiz.getQuestions();
        for (int i = 0; i < qs.size(); i++) {
            sb.append(i + 1).append(". ").append(qs.get(i).getText()).append('\n');
        }
        return ToolResult.ok(sb.toString());
    }
}
