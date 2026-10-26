package com.studymuse;

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
import com.studymuse.services.MaterialService;
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
        return new StudyAgentController(materials, llm.getProviderName());
    }

    public static void main(String[] args) {
        StudyAgentController controller = buildController();
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        // The Swing GUI is added in a later commit; until then every mode runs the CLI.
        new StudyCLI(controller).run(rest);
    }
}
