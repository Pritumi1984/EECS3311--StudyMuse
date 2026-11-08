package com.studymuse;

import com.studymuse.agent.MemoryManager;
import com.studymuse.agent.ResponseParser;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.config.AppConfig;
import com.studymuse.controller.StudyAgentController;
import com.studymuse.documents.DocumentReaderFactory;
import com.studymuse.documents.SearchIndex;
import com.studymuse.documents.TextChunker;
import com.studymuse.domain.QuestionType;
import com.studymuse.grading.MultipleChoiceGrader;
import com.studymuse.grading.ShortAnswerGrader;
import com.studymuse.llm.LLMClient;
import com.studymuse.llm.LLMProvider;
import com.studymuse.llm.LLMProviderFactory;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.persistence.JsonCourseRepository;
import com.studymuse.progress.ProgressTracker;
import com.studymuse.services.AnalyticsService;
import com.studymuse.services.FlashcardService;
import com.studymuse.services.MaterialAnalysisService;
import com.studymuse.services.MaterialService;
import com.studymuse.services.QAService;
import com.studymuse.services.PlanValidator;
import com.studymuse.services.QuizService;
import com.studymuse.services.StudyPlanService;
import com.studymuse.tools.CreateStudyPlanTool;
import com.studymuse.tools.GenerateFlashcardsTool;
import com.studymuse.tools.GenerateQuizTool;
import com.studymuse.tools.GetProgressTool;
import com.studymuse.tools.GetWeakTopicsTool;
import com.studymuse.tools.MistakesToFlashcardsTool;
import com.studymuse.tools.SearchMaterialsTool;
import com.studymuse.tools.ToolRegistry;
import com.studymuse.services.SpacedRepetitionScheduler;
import com.studymuse.ui.MainWindow;
import com.studymuse.ui.StudyCLI;

import java.util.Arrays;
import java.util.Map;

/** Entry point: wires the layers together, then starts the CLI ("cli") or the GUI (default). */
public final class Main {

    private Main() {
    }

    public static StudyAgentController buildController() {
        AppConfig config = AppConfig.getInstance();
        LLMProviderFactory factory = new LLMProviderFactory();
        LLMProvider provider = factory.create(config);
        if (factory.getWarning() != null) {
            System.out.println(factory.getWarning());
        }
        LLMClient llm = new LLMClient(provider);
        ProgressTracker tracker = new ProgressTracker();

        CourseRepository repository = new JsonCourseRepository(config.getDataDir());
        SearchIndex index = new SearchIndex();
        MaterialService materials = new MaterialService(new DocumentReaderFactory(), new TextChunker(), index, repository);
        materials.rebuildIndex();
        StructuredLLMCall ai = new StructuredLLMCall(llm, new ResponseParser());
        MaterialAnalysisService analysis = new MaterialAnalysisService(ai, repository);
        QAService qa = new QAService(index, ai);
        FlashcardService flashcards = new FlashcardService(ai, index, repository, new SpacedRepetitionScheduler(), tracker);
        QuizService quizzes = new QuizService(ai, index, repository, Map.of(
                QuestionType.MULTIPLE_CHOICE, new MultipleChoiceGrader(),
                QuestionType.SHORT_ANSWER, new ShortAnswerGrader(ai)), tracker);
        AnalyticsService analytics = new AnalyticsService(ai, repository);
        StudyPlanService planner = new StudyPlanService(ai, new PlanValidator(), repository, tracker);
        StudyAgentController controller = new StudyAgentController(materials, analysis, qa, flashcards, quizzes,
                analytics, planner, tracker, llm.getProviderName());

        // F12: the agent's tools wrap the same services the GUI and CLI use
        ToolRegistry tools = new ToolRegistry();
        tools.register(new SearchMaterialsTool(index));
        tools.register(new GenerateQuizTool(quizzes, materials));
        tools.register(new GenerateFlashcardsTool(flashcards, materials));
        tools.register(new MistakesToFlashcardsTool(flashcards, materials));
        tools.register(new GetWeakTopicsTool(analytics, materials));
        tools.register(new CreateStudyPlanTool(planner, analytics, materials));
        tools.register(new GetProgressTool(tracker, materials));
        controller.attachAgent(tools, new MemoryManager(repository));
        return controller;
    }

    public static void main(String[] args) {
        StudyAgentController controller = buildController();
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            new StudyCLI(controller).run(Arrays.copyOfRange(args, 1, args.length));
        } else {
            MainWindow.launch(controller);
        }
    }
}
