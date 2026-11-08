# StudyMuse — AI Study Agent (EECS3311 Course Project)

StudyMuse is a Java desktop study assistant: a student uploads course materials, and an AI agent turns them into summaries, answers, flashcards, quizzes, weak-topic analysis and a personalized study plan. It has a Swing GUI and a command-line interface that share the same back end.

**Author:** Pritumi Patel (Student # 220113734)

## Stage 1 — Design (submitted)

* [Stage 1 Design Report (PDF)](StudyMuse_Stage1_Report_FINAL.pdf)
* [diagrams](StudyMuse_UMLet_diagrams) — all UML diagrams drawn in UMLet: `.uxf` source files plus full-size `.png` images

  * CD-1 to CD-4: class diagrams
  * UC-1: use-case diagram
  * SD-01 to SD-09: sequence diagrams

## Stage 2 — Implementation

### Prerequisites

* **Java JDK 17 or newer** (check with `java -version`). The project uses only the standard JDK, so no Maven is needed.
* Optional, for **PDF** uploads: on the first run `run.sh` / `run.bat` downloads Apache PDFBox (one `.jar`) into `lib/` using `curl`. Without internet, StudyMuse still runs; it just accepts only `.txt` and `.md` files. You can also download [pdfbox-app-3.0.3.jar](https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/3.0.3/pdfbox-app-3.0.3.jar) yourself and put it in `lib/`.
* An LLM API key. The default is **Google Gemini** (free key at [aistudio.google.com](https://aistudio.google.com) → *Get API key*). Anthropic Claude is also supported.

### Configure your API key (kept local, never committed)

1. Copy the example settings file:
   * Windows: `copy .env.example .env`
   * macOS/Linux: `cp .env.example .env`
2. Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your key.
3. Optional: set `LLM_PROVIDER=claude` with a Claude key, or `LLM_PROVIDER=mock` to run offline with placeholder AI answers.

`.env` is listed in `.gitignore`, so your key is never pushed to GitHub. Without a key the app still starts, in offline mock mode.

### Run

| | Windows | macOS / Linux |
|---|---|---|
| GUI | `run.bat` (or double-click it) | `./run.sh` |
| CLI | `run.bat cli` | `./run.sh cli` |

The scripts compile the code into `out/` and start the app. The GUI opens on the Dashboard; pick a course on the left, then use the tabs. Course data is saved in `data/`, and LLM call statistics (tokens, time) go to `logs/llm-calls.log`.

### Example CLI session

```
studymuse> course create EECS3311
studymuse> upload eecs3311 sample-data/design-patterns-notes.txt
studymuse> upload eecs3311 sample-data/uml-notes.md
studymuse> summarize eecs3311 uml-notes.md short
studymuse> ask eecs3311 "What problem does the Decorator pattern solve?"
studymuse> concepts eecs3311 design-patterns-notes.txt
studymuse> flashcards generate eecs3311 design-patterns-notes.txt 10
studymuse> flashcards review eecs3311
studymuse> quiz generate eecs3311 --topics "Observer,Decorator" --count 5 --difficulty medium --types mc,sa
studymuse> quizzes eecs3311
studymuse> quiz take eecs3311 <quizId>
studymuse> weak-topics eecs3311
studymuse> plan eecs3311 --exam 2026-12-10 --hours 2 --topics "Observer,Decorator,UML"
studymuse> plan eecs3311 done 2026-11-09 1
studymuse> progress eecs3311
studymuse> chat eecs3311
you> Quiz me on the Observer pattern with 5 questions
you> Turn my quiz mistakes into flashcards
you> My exam is on December 10, make me a study plan with 2 hours a day
you> back
studymuse> tools
studymuse> memory eecs3311
studymuse> help
```

A single command also works without the interactive prompt: `run.bat cli ask eecs3311 "What is a Facade?"`.

### Implemented so far

| Feature | Status | GUI | CLI |
|---|---|---|---|
| F01 Course and material upload (.txt, .md, .pdf) | Implemented | New Course / Upload Material | `course create`, `upload`, `materials` |
| F02 Material summarization | Implemented | Summarize tab | `summarize` |
| F03 Question answering over materials (with cited sources) | Implemented | Ask tab | `ask` |
| F04 Key concept extraction | Implemented | Concepts tab | `concepts` |
| F05 Flashcard generation (preview, edit, save) | Implemented | Flashcards tab | `flashcards generate`, `decks` |
| F06 Flashcard review with spaced repetition (SM-2) | Implemented | Review tab | `flashcards review` |
| F07 Quiz generation (multiple choice + short answer) | Implemented | Quiz tab | `quiz generate`, `quizzes` |
| F08 Quiz grading and feedback | Implemented | Quiz tab → Submit Answers | `quiz take` |
| F09 Weak-topic identification | Implemented | Weak Topics tab | `weak-topics` |
| F10 Personalized study plan (AI plan, validated and fixed, fallback plan) | Implemented | Study Plan tab (double-click a session to mark it done) | `plan`, `plan <course> show`, `plan <course> done` |
| F11 Progress dashboard (Observer pattern, live updates) | Implemented | Dashboard tab | `progress` |
| F12 AI study agent: plans steps, calls 7 tools, remembers facts such as the exam date | Implemented | Agent Chat tab | `chat`, `tools`, `tool`, `memory` |

**How the agent works (F12):** each request runs a loop of up to 10 steps. The LLM sees the request, the course memory (exam date, recent conversation), the list of tools and the results so far, and replies with one JSON action: call a tool, give the final answer, or ask a clarifying question. Tool calls are checked before they run (known tool, valid arguments), and errors are sent back to the LLM so it can correct itself. The agent can only act on the selected course. Tools: `search_materials`, `generate_quiz`, `generate_flashcards`, `flashcards_from_mistakes`, `get_weak_topics`, `create_study_plan`, `get_progress`.

### Source layout

```
src/main/java/com/studymuse/
  Main.java        wires the layers, starts GUI or CLI
  ui/              MainWindow (Swing GUI), feature panels, DashboardView, AgentChatPanel, StudyCLI
  controller/      StudyAgentController (Facade)
  services/        MaterialService, MaterialAnalysisService, QAService, FlashcardService,
                   SpacedRepetitionScheduler, QuizService, AnalyticsService, StudyPlanService, PlanValidator
  progress/        ProgressTracker (Observer subject), ProgressObserver, ProgressEvent, ProgressStats
  tools/           StudyTool, ToolRegistry, ToolResult and the 7 agent tools
  grading/         GradingStrategy (Strategy), MultipleChoiceGrader, ShortAnswerGrader
  agent/           AgentPlanner (agent loop), MemoryManager, PromptBuilder (Builder), ResponseParser,
                   StructuredLLMCall, AgentAction, AgentResponse
  llm/             LLMProvider (Strategy), Gemini/Claude adapters, Mock, Retry/Logging decorators, factory
  documents/       DocumentReaderFactory (Factory Method), PdfDocumentReader (Adapter for PDFBox),
                   TextChunker, SearchIndex
  persistence/     CourseRepository, JsonCourseRepository
  domain/          Course, Material, Chunk, Summary, Concept, Answer, Deck, Flashcard, Quiz,
                   Question, QuizResult, GradeResult, WeakTopic, StudyPlan, ChatMessage, ...
  config/          AppConfig (Singleton)
sample-data/       safe example study notes for trying the app
```

## Stage 3 — Testing

*Coming later.*

