package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.ResponseFormatException;
import com.studymuse.agent.ResponseParser;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.domain.Chunk;
import com.studymuse.domain.Concept;
import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.domain.Summary;
import com.studymuse.domain.SummaryLength;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.util.Json;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F02 summarization and F04 key concept extraction (AI). Long materials are processed in groups of
 * chunks so every LLM call stays within the model's context limit.
 */
public class MaterialAnalysisService {

    static final int CHUNKS_PER_CALL = 8;
    static final String SUMMARY_FORMAT = "{\"overview\": \"...\", \"keyPoints\": [\"...\", \"...\"]}";

    private final StructuredLLMCall ai;
    private final ResponseParser parser;
    private final CourseRepository repository;

    public MaterialAnalysisService(StructuredLLMCall ai, CourseRepository repository) {
        this.ai = ai;
        this.parser = ai.parser();
        this.repository = repository;
    }

    public Summary summarize(String courseId, Material material, SummaryLength length) {
        if (!material.hasText()) {
            throw new StudyException("'" + material.getFileName() + "' has no text to summarize.");
        }
        List<Chunk> chunks = material.getChunks();
        try {
            Map<String, Object> json;
            if (chunks.size() <= CHUNKS_PER_CALL) {
                json = askForSummary(chunks, length, "Summarize this course material for a student.");
            } else {
                // map step: summarize each group of chunks; reduce step: combine the partial summaries
                List<Chunk> partials = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i += CHUNKS_PER_CALL) {
                    List<Chunk> group = chunks.subList(i, Math.min(chunks.size(), i + CHUNKS_PER_CALL));
                    Map<String, Object> part = askForSummary(group, SummaryLength.MEDIUM, "Summarize this part of the material.");
                    String text = parser.requireText(part, "overview") + " " + String.join(" ", parser.textList(part, "keyPoints"));
                    partials.add(new Chunk(material.getId() + "-part" + partials.size(), material.getId(),
                            material.getFileName(), partials.size(), text));
                }
                json = askForSummary(partials, length, "Combine these partial summaries into one summary of the whole material.");
            }
            Summary summary = new Summary(parser.requireText(json, "overview"), parser.textList(json, "keyPoints"), length,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            material.setSummary(summary);
            repository.saveMaterial(courseId, material);
            return summary;
        } catch (ResponseFormatException e) {
            throw new StudyException("The AI summary was incomplete: " + e.getMessage(), e);
        }
    }

    /**
     * F04: asks the LLM for the key concepts of a material in a fixed JSON format, keeps only valid
     * entries (name + definition, source chunk must exist), merges duplicates and saves them on the course.
     */
    public List<Concept> extractConcepts(Course course, Material material) {
        if (!material.hasText()) {
            throw new StudyException("'" + material.getFileName() + "' has no text to extract concepts from.");
        }
        List<Chunk> chunks = material.getChunks();
        List<Concept> found = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += CHUNKS_PER_CALL) {
            List<Chunk> group = chunks.subList(i, Math.min(chunks.size(), i + CHUNKS_PER_CALL));
            Map<String, Object> json = ai.ask(new PromptBuilder()
                    .withSystemInstructions("You are StudyMuse, a study assistant. Identify the important concepts and terms "
                            + "a student must know from the course material. Give each a one-sentence definition based only "
                            + "on the material, and the id of the chunk it comes from.")
                    .withContext(group)
                    .withOutputFormat("{\"concepts\": [{\"name\": \"...\", \"definition\": \"...\", \"sourceChunkId\": \"<chunk id>\"}]}")
                    .withUserRequest("List up to 12 key concepts from this material.")
                    .build());
            for (Object o : Json.list(json, "concepts")) {
                Map<String, Object> c = Json.obj(o);
                String name = Json.str(c, "name");
                String definition = Json.str(c, "definition");
                String source = Json.str(c, "sourceChunkId");
                if (name == null || name.isBlank() || definition == null || definition.isBlank()) {
                    continue;
                }
                boolean sourceExists = group.stream().anyMatch(ch -> ch.getId().equals(source));
                boolean duplicate = found.stream().anyMatch(f -> f.getName().equalsIgnoreCase(name.trim()));
                if (!duplicate) {
                    found.add(new Concept(name.trim(), definition.trim(), sourceExists ? source : null));
                }
            }
        }
        if (found.isEmpty()) {
            throw new StudyException("No concepts were found - the material may be too short.");
        }
        course.mergeConcepts(found);
        repository.saveCourse(course);
        return found;
    }

    private Map<String, Object> askForSummary(List<Chunk> chunks, SummaryLength length, String request) {
        return ai.ask(new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, a study assistant. Use only the course material given.")
                .withSystemInstructions("Length: " + length.getInstruction() + ".")
                .withContext(chunks)
                .withOutputFormat(SUMMARY_FORMAT)
                .withUserRequest(request)
                .build());
    }
}
