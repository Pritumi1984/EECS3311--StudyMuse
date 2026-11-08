package com.studymuse.agent;

import com.studymuse.domain.ChatMessage;
import com.studymuse.domain.Course;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.services.StudyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's memory (F12), saved with each course so it survives restarts:
 * short-term = the recent conversation (ConversationHistory), long-term = key facts such as the exam date.
 * Facts are captured deterministically from the student's messages and can also be stored by the agent.
 */
public class MemoryManager {

    static final int CONTEXT_MESSAGES = 6;
    private static final Pattern ISO_EXAM = Pattern.compile("(?i)exam[^.?!\\n]{0,40}?(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern MONTH_EXAM = Pattern.compile(
            "(?i)exam[^.?!\\n]{0,40}?\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b");

    private final CourseRepository repository;

    public MemoryManager(CourseRepository repository) {
        this.repository = repository;
    }

    public void addMessage(String courseId, ChatMessage message) {
        Course course = load(courseId);
        course.addChatMessage(message, ConversationHistory.MAX_MESSAGES);
        if ("user".equals(message.role())) {
            String examDate = extractExamDate(message.text(), LocalDate.now());
            if (examDate != null) {
                course.putAgentFact("exam date", examDate);
            }
        }
        repository.saveCourse(course);
    }

    public void rememberFact(String courseId, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        Course course = load(courseId);
        course.putAgentFact(key.trim().toLowerCase(Locale.ROOT), value.trim());
        repository.saveCourse(course);
    }

    public Map<String, String> facts(String courseId) {
        return load(courseId).getAgentFacts();
    }

    public ConversationHistory history(String courseId) {
        return new ConversationHistory(load(courseId).getChatHistory(), ConversationHistory.MAX_MESSAGES);
    }

    /** Text block for the prompt: today's date, remembered facts and the last few turns. */
    public String getRelevantContext(String courseId, String request) {
        StringBuilder sb = new StringBuilder("Today is " + LocalDate.now() + ".\n");
        Map<String, String> facts = facts(courseId);
        if (!facts.isEmpty()) {
            sb.append("Remembered facts:\n");
            facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        }
        var recent = history(courseId).recent(CONTEXT_MESSAGES);
        if (!recent.isEmpty()) {
            sb.append("Recent conversation:\n");
            for (ChatMessage m : recent) {
                String t = m.text().length() > 400 ? m.text().substring(0, 400) + "..." : m.text();
                sb.append(m.role()).append(": ").append(t.replace('\n', ' ')).append('\n');
            }
        }
        return sb.toString();
    }

    /** Finds "exam ... 2026-11-20" or "exam ... Nov 20" in a message; returns ISO date or null. */
    static String extractExamDate(String text, LocalDate today) {
        Matcher iso = ISO_EXAM.matcher(text);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group(1)).toString();
            } catch (RuntimeException e) {
                return null;
            }
        }
        Matcher m = MONTH_EXAM.matcher(text);
        if (m.find()) {
            for (Month month : Month.values()) {
                if (month.name().toLowerCase(Locale.ROOT).startsWith(m.group(1).toLowerCase(Locale.ROOT))) {
                    try {
                        LocalDate d = LocalDate.of(today.getYear(), month, Integer.parseInt(m.group(2)));
                        return (d.isBefore(today) ? d.plusYears(1) : d).toString();
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static ChatMessage message(String role, String text) {
        return new ChatMessage(role, text, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private Course load(String courseId) {
        return repository.findCourse(courseId).orElseThrow(() -> new StudyException("No course with id '" + courseId + "'."));
    }
}
