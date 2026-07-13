# EchoNote

An AI-powered, cross-platform note-taking app built with Kotlin Multiplatform and Compose Multiplatform. Notes aren't just written — they can be expanded or condensed by AI on demand, with full undo support and real-time sync across devices.

## Features

- **AI-assisted editing** — Expand a short note into a fuller draft, or condense a long one into key bullet points, powered by Gemini (`gemini-2.5-flash`). Output streams in word-by-word with a typewriter effect.
- **Undo for AI edits** — Every AI transformation is pushed onto a per-note undo stack (up to 20 steps) before it overwrites the content, so a bad generation is always one tap away from reverting.
- **Live Markdown highlighting** — Headings, bold/italic, inline code, list markers, and blockquotes are syntax-highlighted directly in the text field as you type, without altering the underlying text.
- **Adaptive layout** — A responsive split-pane layout for desktop/tablet (list + editor side by side) that collapses into a full-screen list on narrow/mobile widths, with the editor sliding in as an overlay.
- **Real-time multi-device sync** — Backed by Supabase (Postgrest + Realtime), so changes made on one device stream live to every other connected client.
- **Offline-first fallback** — If no API keys are configured, the app transparently falls back to an in-memory note repository and a deterministic mock AI, so it's always fully usable without any backend.
- **Optimistic UI** — Note creation and deletion update the UI instantly and reconcile with the backend afterward, rather than waiting on a round-trip.
- **Conflict resolution** — Incoming realtime updates are merged against local unsaved ("dirty") edits using a last-write-wins strategy based on `updated_at` and a per-device ID.
- **Debounced autosave** — Local edits are persisted 600ms after typing stops, avoiding a write on every keystroke.

## Architecture

EchoNote is a Kotlin Multiplatform project with the UI and business logic living in a single shared module, consumed by thin platform-specific app shells.

```
EchoNote/
├── androidApp/          # Android application shell (MainActivity, manifest, resources)
├── desktopApp/           # Desktop (JVM) application shell (main entry point)
├── shared/                # Shared KMP module — UI, state, and business logic
│   └── src/
│       ├── commonMain/    # Platform-agnostic code
│       │   └── kotlin/com/echonote/echonote/
│       │       ├── ai/           # AiService abstraction: GeminiAiService + MockAiService
│       │       ├── data/         # NotesRepository abstraction: SupabaseNotesRepository + InMemoryNotesRepository
│       │       ├── model/        # Note data model, sample data, mock AI transforms
│       │       ├── App.kt              # Root composable, responsive layout switch
│       │       ├── NotesViewModel.kt   # UI state, sync, undo, AI orchestration
│       │       ├── NoteListPane.kt     # Note list UI
│       │       ├── NoteEditorPane.kt   # Note editor UI
│       │       ├── MarkdownHighlighter.kt  # Inline Markdown syntax highlighting
│       │       ├── Theme.kt            # App theming
│       │       └── Services.kt         # Service factories (online vs. offline mode)
│       ├── androidMain/   # Android-specific implementations
│       └── jvmMain/       # Desktop (JVM)-specific implementations
└── gradle/                # Version catalog and wrapper
```

**Key abstractions:**

- `NotesRepository` — `SupabaseNotesRepository` for real backend sync via Postgrest + Realtime, or `InMemoryNotesRepository` as an offline fallback. Selected automatically at startup based on whether Supabase credentials are present.
- `AiService` — `GeminiAiService` for real Gemini REST calls over Ktor, or `MockAiService` for deterministic offline text transforms. Selected automatically based on whether a Gemini API key is present.
- `NotesViewModel` — owns UI state (`NotesUiState`), coordinates repository sync, per-note undo stacks, save debouncing, and the AI streaming pipeline.

Credentials (Gemini API key, Supabase URL/anon key) live in a git-ignored `Secrets.kt` object; leaving any of them blank switches that part of the app into its offline counterpart automatically.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (Multiplatform) |
| UI | Compose Multiplatform (Material 3) |
| State | Android Lifecycle ViewModel + Kotlin Coroutines / Flow |
| Networking | Ktor Client (CIO engine) |
| Serialization | kotlinx.serialization |
| Backend / DB | Supabase (Postgrest + Realtime) |
| AI | Google Gemini API (`gemini-2.5-flash`) |
| Date/Time | kotlinx-datetime |
| Targets | Android, Desktop (JVM) |

## Data Model

Notes map 1:1 to a Supabase `notes` table:

```kotlin
data class Note(
    val id: String,          // UUID
    val title: String,
    val content: String,     // Markdown
    val updatedAt: String,   // ISO-8601 UTC, used for conflict resolution
    val deviceId: String,    // Origin device, used for conflict resolution
)
```

## License

No license file is currently included in this repository.
