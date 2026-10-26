# StudyMuse — AI Study Agent (EECS3311 Course Project)

StudyMuse is a Java desktop study assistant: a student uploads course materials, and an AI agent turns them into summaries, answers, flashcards, quizzes, weak-topic analysis and a personalized study plan. It has a Swing GUI and a command-line interface that share the same back end.

**Author:** Pritumi Patel (Student # 220113734)

## Stage 1 — Design (submitted)

* [Stage 1 Design Report (PDF)](StudyMuse_Stage1_Report_FINAL.pdf)
* [diagrams](StudyMuse_UMLet_diagrams) — all UML diagrams drawn in UMLet: `.uxf` source files plus full-size `.png` images

  * CD-1 to CD-4: class diagrams
  * UC-1: use-case diagram
  * SD-01 to SD-09: sequence diagrams

## Stage 2 — Implementation (in progress)

### Prerequisites

* **Java JDK 17 or newer** (check with `java -version`). Nothing else: the project uses only the standard JDK, so no Maven or extra libraries are needed.
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

The scripts compile the code into `out/` and start the app. Course data is saved in `data/`, and LLM call statistics (tokens, time) go to `logs/llm-calls.log`.

### Example CLI session

```
studymuse> course create EECS3311
studymuse> upload eecs3311 sample-data/design-patterns-notes.txt
studymuse> upload eecs3311 sample-data/uml-notes.md
studymuse> summarize eecs3311 uml-notes.md short
studymuse> ask eecs3311 "What problem does the Decorator pattern solve?"
studymuse> concepts eecs3311 design-patterns-notes.txt
studymuse> help
```

A single command also works without the interactive prompt: `run.bat cli ask eecs3311 "What is a Facade?"`.

### Implemented so far

| Feature | Status | GUI | CLI |
|---|---|---|---|
| F01 Course and material upload (.txt, .md) | Implemented | New Course / Upload Material | `course create`, `upload`, `materials` |
| F02 Material summarization | Implemented | Summarize tab | `summarize` |
| F03 Question answering over materials (with cited sources) | Implemented | Ask tab | `ask` |
| F04 Key concept extraction | Implemented | Concepts tab | `concepts` |
| F05–F12 | Planned for the next commits | | |

### Source layout

```
src/main/java/com/studymuse/
  Main.java        wires the layers, starts GUI or CLI
  ui/              MainWindow (Swing GUI), StudyCLI, StudyView
  controller/      StudyAgentController (Facade)
  services/        MaterialService, MaterialAnalysisService, QAService
  agent/           PromptBuilder (Builder), ResponseParser, StructuredLLMCall
  llm/             LLMProvider (Strategy), Gemini/Claude adapters, Mock, Retry/Logging decorators, factory
  documents/       DocumentReaderFactory (Factory Method), TextChunker, SearchIndex
  persistence/     CourseRepository, JsonCourseRepository
  domain/          Course, Material, Chunk, Summary, Concept, Answer
  config/          AppConfig (Singleton)
sample-data/       safe example study notes for trying the app
```

## Stage 3 — Testing

*Coming later.*

