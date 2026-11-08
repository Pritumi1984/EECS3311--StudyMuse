package com.studymuse.services;

import com.studymuse.agent.PromptBuilder;
import com.studymuse.agent.ResponseFormatException;
import com.studymuse.agent.StructuredLLMCall;
import com.studymuse.domain.ActivityType;
import com.studymuse.domain.Concept;
import com.studymuse.domain.Course;
import com.studymuse.domain.PlanRequest;
import com.studymuse.domain.StudyDay;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.StudySession;
import com.studymuse.domain.WeakTopic;
import com.studymuse.llm.Message;
import com.studymuse.llm.Prompt;
import com.studymuse.persistence.CourseRepository;
import com.studymuse.progress.ProgressTracker;
import com.studymuse.util.Json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F10 personalized study plan (hybrid, generate-then-validate):
 * the LLM drafts a plan from the weak topics and concepts; PlanValidator checks it; the LLM is asked to
 * fix the listed problems at most twice; if it is still invalid, a deterministic fallback plan
 * (equal time per topic, weak topics first) is used.
 */
public class StudyPlanService {

    public static final int MAX_FIX_ATTEMPTS = 2;
    public static final int MAX_PLAN_DAYS = 14;
    static final int MIN_MINUTES_PER_TOPIC = 30;
    static final String PLAN_FORMAT = "{\"days\": [{\"date\": \"YYYY-MM-DD\", \"sessions\": [{\"topic\": \"...\", "
            + "\"activity\": \"READ_NOTES | REVIEW_FLASHCARDS | TAKE_QUIZ | PRACTICE_QUESTIONS\", \"minutes\": 45}]}]}";

    private final StructuredLLMCall ai;
    private final PlanValidator validator;
    private final CourseRepository repository;
    private final ProgressTracker tracker;

    public StudyPlanService(StructuredLLMCall ai, PlanValidator validator, CourseRepository repository,
                            ProgressTracker tracker) {
        this.tracker = tracker;
        this.ai = ai;
        this.validator = validator;
        this.repository = repository;
    }

    public StudyPlan generatePlan(Course course, PlanRequest req, List<WeakTopic> weakTopics, LocalDate today) {
        if (req.examDate() == null || !req.examDate().isAfter(today)) {
            throw new StudyException("The exam date must be after today.");
        }
        if (req.dailyHours() <= 0 || req.dailyHours() > 12) {
            throw new StudyException("Hours per day must be between 0.5 and 12.");
        }
        LocalDate firstDay = today;
        LocalDate lastDay = req.examDate().minusDays(1);
        if (lastDay.isAfter(today.plusDays(MAX_PLAN_DAYS - 1))) {
            lastDay = today.plusDays(MAX_PLAN_DAYS - 1); // plan the next two weeks; regenerate later for the rest
        }
        List<LocalDate> dates = firstDay.datesUntil(lastDay.plusDays(1)).toList();
        int dailyMinutes = req.dailyMinutes();

        // topics: requested ones, or every concept and weak topic of the course; weak topics go first
        Set<String> weakNames = weakTopics.stream().map(w -> w.topic().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> candidates = new LinkedHashSet<>();
        for (WeakTopic w : weakTopics) {
            if (req.topics().isEmpty() || containsIgnoreCase(req.topics(), w.topic())) {
                candidates.add(w.topic());
            }
        }
        if (req.topics().isEmpty()) {
            for (Concept c : course.getConcepts()) {
                candidates.add(c.getName());
            }
        } else {
            candidates.addAll(req.topics());
        }
        List<String> topics = dedupe(candidates);
        if (topics.isEmpty()) {
            throw new StudyException("No topics to plan. Enter topics, or extract concepts from your materials first.");
        }
        // too little time: keep as many topics as fit at 30 min each (weak topics are first in the list)
        int capacity = Math.max(1, dates.size() * dailyMinutes / MIN_MINUTES_PER_TOPIC);
        List<String> included = new ArrayList<>(topics.subList(0, Math.min(topics.size(), capacity)));
        List<String> omitted = new ArrayList<>(topics.subList(included.size(), topics.size()));

        String created = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        StudyPlan plan = null;
        List<String> problems = List.of("No plan yet.");
        Prompt prompt = draftPrompt(dates, dailyMinutes, included, weakNames, req.examDate());
        String lastReply = null;
        for (int attempt = 0; attempt <= MAX_FIX_ATTEMPTS && !problems.isEmpty(); attempt++) {
            if (attempt > 0) {
                prompt = fixPrompt(prompt, lastReply, problems);
            }
            try {
                Map<String, Object> json = ai.ask(prompt);
                lastReply = Json.compact(json);
                plan = parsePlan(json, req, created, omitted);
                problems = validator.validate(plan, dailyMinutes, firstDay, req.examDate(), included);
            } catch (ResponseFormatException e) {
                problems = List.of("The reply did not match the required JSON format: " + e.getMessage());
            } catch (StudyException e) {
                problems = List.of(e.getMessage());
                break; // AI service unavailable: go straight to the fallback
            }
        }
        if (!problems.isEmpty()) {
            plan = createFallbackPlan(dates, dailyMinutes, included, req, created, omitted);
        }
        course.setStudyPlan(plan);
        repository.savePlan(course.getId(), plan);
        return plan;
    }

    /** F11: ticks a plan session as done (or not done); position is its order within the day (0-based). */
    public StudySession setSessionDone(Course course, LocalDate date, int position, boolean done) {
        StudyPlan plan = course.getStudyPlan();
        if (plan == null) {
            throw new StudyException("There is no study plan yet.");
        }
        for (StudyDay d : plan.getDays()) {
            if (d.getDate().equals(date)) {
                List<StudySession> sessions = d.getSessions();
                if (position < 0 || position >= sessions.size()) {
                    throw new StudyException(date + " has no session number " + (position + 1) + ".");
                }
                StudySession s = sessions.get(position);
                s.setDone(done);
                repository.savePlan(course.getId(), plan);
                tracker.recordPlanSession(course.getId(), s);
                return s;
            }
        }
        throw new StudyException("The plan has no sessions on " + date + ".");
    }

    private Prompt draftPrompt(List<LocalDate> dates, int dailyMinutes, List<String> topics, Set<String> weak,
                               LocalDate exam) {
        String topicLines = topics.stream()
                .map(t -> "- " + t + (weak.contains(t.toLowerCase(Locale.ROOT)) ? " (WEAK: give it more time)" : ""))
                .collect(Collectors.joining("\n"));
        return new PromptBuilder()
                .withSystemInstructions("You are StudyMuse, a study coach who builds realistic day-by-day study plans.")
                .withOutputFormat(PLAN_FORMAT)
                .withUserRequest("Build a study plan for an exam on " + exam + ".\n"
                        + "PLAN DATES: " + dates.stream().map(LocalDate::toString).collect(Collectors.joining(", ")) + "\n"
                        + "DAILY MINUTES: " + dailyMinutes + " (never exceed this on any date)\n"
                        + "TOPICS TO INCLUDE (every one at least once):\n" + topicLines + "\n"
                        + "Use only the listed dates and topics. Mix activities: start a topic with READ_NOTES, then "
                        + "REVIEW_FLASHCARDS and finish with TAKE_QUIZ closer to the exam.")
                .build();
    }

    /** Sends the draft back with the validator's problem list so the LLM can correct it. */
    private Prompt fixPrompt(Prompt original, String previousReply, List<String> problems) {
        List<Message> msgs = new ArrayList<>(original.messages());
        msgs.add(new Message("assistant", previousReply == null ? "{}" : previousReply));
        msgs.add(new Message("user", "Your plan has these PROBLEMS:\n- " + String.join("\n- ", problems)
                + "\nReturn the complete corrected plan as JSON."));
        return new Prompt(original.system(), msgs);
    }

    private StudyPlan parsePlan(Map<String, Object> json, PlanRequest req, String created, List<String> omitted) {
        Map<LocalDate, List<StudySession>> byDate = new LinkedHashMap<>();
        for (Object o : Json.list(json, "days")) {
            Map<String, Object> d = Json.obj(o);
            LocalDate date;
            try {
                date = LocalDate.parse(String.valueOf(Json.str(d, "date")).trim());
            } catch (DateTimeParseException e) {
                throw new ResponseFormatException("bad date '" + Json.str(d, "date") + "'");
            }
            List<StudySession> sessions = byDate.computeIfAbsent(date, k -> new ArrayList<>());
            for (Object so : Json.list(d, "sessions")) {
                Map<String, Object> s = Json.obj(so);
                ActivityType activity = ActivityType.parseOrNull(Json.str(s, "activity"));
                String topic = Json.str(s, "topic");
                if (activity == null || topic == null || topic.isBlank()) {
                    throw new ResponseFormatException("a session has an unknown activity or no topic");
                }
                sessions.add(new StudySession(topic.trim(), activity, Json.integer(s, "minutes", 0), false));
            }
        }
        List<StudyDay> days = new ArrayList<>();
        byDate.forEach((date, sessions) -> days.add(new StudyDay(date, sessions)));
        days.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return new StudyPlan(req.examDate(), req.dailyHours(), created, days, omitted, "AI plan, validated");
    }

    /** Deterministic backup: rotates through the topics (weak first) with equal time, cycling activities. */
    StudyPlan createFallbackPlan(List<LocalDate> dates, int dailyMinutes, List<String> topics, PlanRequest req,
                                 String created, List<String> omitted) {
        int sessionsPerDay = Math.max(1, Math.min(topics.size(), dailyMinutes / MIN_MINUTES_PER_TOPIC));
        int minutesEach = dailyMinutes / sessionsPerDay;
        ActivityType[] cycle = {ActivityType.READ_NOTES, ActivityType.REVIEW_FLASHCARDS, ActivityType.TAKE_QUIZ};
        List<StudyDay> days = new ArrayList<>();
        int next = 0;
        for (int d = 0; d < dates.size(); d++) {
            List<StudySession> sessions = new ArrayList<>();
            for (int s = 0; s < sessionsPerDay; s++) {
                String topic = topics.get(next % topics.size());
                ActivityType activity = cycle[Math.min(cycle.length - 1, (next / topics.size()) % cycle.length)];
                sessions.add(new StudySession(topic, activity, minutesEach, false));
                next++;
            }
            days.add(new StudyDay(dates.get(d), sessions));
        }
        return new StudyPlan(req.examDate(), req.dailyHours(), created, days, omitted, "fallback plan (equal time per topic)");
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        return list.stream().anyMatch(x -> x.equalsIgnoreCase(s));
    }

    private static List<String> dedupe(Set<String> names) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String n : names) {
            if (n != null && !n.isBlank() && seen.add(n.trim().toLowerCase(Locale.ROOT))) {
                out.add(n.trim());
            }
        }
        return out;
    }
}
