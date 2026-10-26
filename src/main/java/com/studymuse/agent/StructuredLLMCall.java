package com.studymuse.agent;

import com.studymuse.llm.LLMClient;
import com.studymuse.llm.LLMException;
import com.studymuse.llm.Prompt;
import com.studymuse.services.StudyException;

import java.util.Map;

/**
 * Sends a prompt that asks for JSON and returns the parsed object. A malformed reply is retried
 * once with a format reminder; LLM failures become friendly StudyExceptions for the UI.
 */
public class StructuredLLMCall {

    private final LLMClient llm;
    private final ResponseParser parser;

    public StructuredLLMCall(LLMClient llm, ResponseParser parser) {
        this.llm = llm;
        this.parser = parser;
    }

    public Map<String, Object> ask(Prompt prompt) {
        try {
            String raw = llm.complete(prompt);
            try {
                return parser.parseJson(raw);
            } catch (ResponseFormatException first) {
                Prompt retry = new Prompt(prompt.system()
                        + "\nIMPORTANT: your previous reply was not valid JSON. Reply with the JSON object only.", prompt.messages());
                return parser.parseJson(llm.complete(retry));
            }
        } catch (LLMException e) {
            throw new StudyException("The AI service failed: " + e.getMessage(), e);
        } catch (ResponseFormatException e) {
            throw new StudyException("The AI reply could not be understood: " + e.getMessage(), e);
        }
    }

    public ResponseParser parser() {
        return parser;
    }
}
