package com.studymuse;

import com.studymuse.agent.ResponseParser;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.config.AppConfig;
import com.studymuse.controller.StudyAgentController;
import com.studymuse.documents.DocumentReaderFactory;
import com.studymuse.documents.SearchIndex;
import com.studymuse.documents.TextChunker;
import com.studymuse.llm.LLMClient;
import com.studymuse.llm.LLMProvider;
import com.studymuse.llm.LLMProviderFactory;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.persistence.JsonCourseRepository;
import com.studymuse.services.MaterialAnalysisService;
import com.studymuse.services.MaterialService;
import com.studymuse.services.QAService;
import com.studymuse.ui.MainWindow;
import com.studymuse.ui.StudyCLI;

import java.util.Arrays;

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

        CourseRepository repository = new JsonCourseRepository(config.getDataDir());
        SearchIndex index = new SearchIndex();
        MaterialService materials = new MaterialService(new DocumentReaderFactory(), new TextChunker(), index, repository);
        materials.rebuildIndex();
        StructuredLLMCall ai = new StructuredLLMCall(llm, new ResponseParser());
        MaterialAnalysisService analysis = new MaterialAnalysisService(ai, repository);
        QAService qa = new QAService(index, ai);
        return new StudyAgentController(materials, analysis, qa, llm.getProviderName());
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
