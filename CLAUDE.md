# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

## The product vision

TV Quotes: An app for TVs (initially webOS and Android TV) that displays interesting quotes. The two
key features are visually and intelectually pleasing quote screens, and intelligent voice search
that takes various interpretations of quotes into account to provide relevant and thought
provoking results.

## Current scope

The TV app is still a fresh foundation. There is **no** authentication, dependency injection,
navigation framework, or background work in `platforms/android-tv`, and none of that should be
added speculatively. See the "possible future features" list in the original project brief for
context on where this is headed — none of it is implemented, and none of it should be started
without an explicit request. The `admin` website (see below) is the first concrete step into that
list and exists because it was explicitly requested — don't take it as license to build out the
rest of the list unprompted.

Frontends live under `platforms/`, one directory per TV platform, since the product targets
multiple TV systems (webOS and Android TV now, more later). Don't add a new platform directory
speculatively — only when a specific platform is explicitly requested.

## Module responsibilities

- **`platforms/android-tv`** (`no.esotericgames.quotes`) — Native Android TV app. Single activity
  (`MainActivity`), one composable screen (`QuoteScreen`) showing a hard-coded `Quote`. TV theming
  lives in `theme/` (`Theme.kt`, `Color.kt`, `Type.kt`) and uses `androidx.tv.material3`, not the
  phone `androidx.compose.material3` artifact.
- **`platforms/tv-web`** — React + TypeScript + Vite frontend for web-based TV platforms (not a
  Gradle module — a separate npm project, same tooling conventions as `admin`). Browser-testable
  and webOS-packageable (see `platforms/tv-web/README.md` for packaging/install/launch via LG's
  `ares-cli`); written to be reusable for other web-based TV platforms (e.g. Tizen) later. Fetches
  a random batch of quotes from the server's `/api/quotes/random` endpoint and displays one at a
  time, full-screen, with conditional author/source fields, 30s auto-advance, and D-pad/arrow-key
  navigation with wrap-around. Each screen is composed by `src/composition/` from the server-chosen
  mood background and motif elements (layout, text contrast, fallback gradient), rendered by
  `QuoteStage` with crossfades; ArrowUp re-rolls, `?debug` shows what was chosen. `src/platform/` provides `webos`/`browser` detection for future
  platform-specific branching.
- **`server`** (`no.esotericgames.quotes.server`) — Ktor/Netty server. `Application.kt` is the
  entry point (`EngineMain`), `Routing.kt` defines routes, `Models.kt` holds the `@Serializable`
  response types and the hard-coded sample quotes. `PublicQuoteService.kt` backs the public
  `GET /api/quotes/random` endpoint (random verified quotes with author/source, for TV frontends),
  each with `visuals` chosen by `composition/` (a mood background and up to four motif elements).
  `db/` holds the Exposed table definitions and Flyway-migrated PostgreSQL schema (`authors`,
  `sources`, `quotes`, `tags`, `imported_quotes`). `wikiquote/` is the Wikiquote importer, which
  stages results in `imported_quotes`. `extraction/`, `interpretation/` and `tagging/` are the
  LLM-backed enrichment pipelines (excerpts, interpretations, and faceted tags respectively), each
  triggered from the admin. `imagegen/` generates per-tag images on a ComfyUI server (backgrounds for
  mood tags, transparent collage elements for motif tags) as an admin-started background job, stores
  the PNGs under `imageGeneration.storageDir` and records them in `tag_images`; `TagImageFileService`
  serves them, downscaled and cached, at `GET /api/tag-images/{id}?width=`. `admin/` (package, not to be confused with the top-level
  `admin` frontend project) holds the admin API — DTOs and the service backing the
  `/admin/imported-quotes` routes used to review and approve staged imports into real `quotes`
  rows.
- **`admin`** — React + TypeScript + Vite admin website (not a Gradle module — a separate npm
  project). Talks to the `server`'s `/admin/*` routes over plain `fetch`, no auth yet. Lets a human
  filter `imported_quotes` by processing status/confidence/provider and approve, reject, mark
  duplicate, or reset rows; approving creates the corresponding `authors`/`quotes` rows.

## Key architectural decisions

- **AGP 9's built-in Kotlin support** compiles `platforms/android-tv`; the standalone
  `org.jetbrains.kotlin.android` plugin is intentionally *not* applied there. `server` uses the
  standard `org.jetbrains.kotlin.jvm` plugin, since built-in Kotlin only covers Android modules.
- The Ktor Gradle plugin (`io.ktor.plugin`) is applied in `server`; it implicitly manages the Ktor
  BOM, so `io.ktor:*` dependencies are declared without explicit versions.
- No ViewModel in `platforms/android-tv` — there's no state or lifecycle need yet. Add one only
  when a concrete need appears, not preemptively.
- `SERVER_BASE_URL` is a single `buildConfigField` in `platforms/android-tv/build.gradle.kts` — the
  one place to point the app at a server later. It is not wired to a network call yet.

## Dependency and version-management conventions

- All versions live in `gradle/libs.versions.toml`. Never hard-code a version string in a module
  `build.gradle.kts`; add or reuse a catalog entry instead.
- No dynamic versions (`+`) and no unversioned plugin/dependency declarations, except where a BOM
  or the Ktor Gradle plugin intentionally supplies the version.
- Compose libraries covered by the Compose BOM (`androidx.compose:compose-bom`) get their version
  from the BOM, not the catalog. Compose for TV (`androidx.tv:tv-material`,
  `androidx.tv:tv-foundation`) is **not** covered by that BOM and is pinned explicitly.

## LLM features

The server may use locally hosted LLMs for offline/background processing of quote data.

General rules:

* Treat LLM output as untrusted data. Parse and validate all structured responses.
* Prefer schema-constrained structured output over parsing free-form prose.
* Do not allow an LLM to silently rewrite source quotations when the operation is intended to extract an excerpt.
* Preserve the original quote text and provenance.
* Reconstruct extracted quotations deterministically from the original text whenever possible.
* Keep model provider/runtime details behind an application abstraction rather than coupling domain logic directly to llama.cpp.
* LLM endpoints, model names, timeouts, and generation settings must be configurable.
* Prompts used by application code should be version-controlled resources rather than large inline strings in Kotlin source.
* Add deterministic tests around preprocessing, validation, reconstruction, and malformed model responses. Do not make ordinary unit tests depend on a running LLM.

### Quote extraction

When implementing or modifying quotable-excerpt extraction, first read:

`docs/features/quote-extraction.md`

That document defines the intended behavior, extraction constraints, model contract, and quality criteria. Do not substantially change those semantics merely to simplify implementation without documenting the decision.

### Quote interpretations

When implementing or modifying quote interpretation generation, first read:

`docs/features/quote-interpretations.md`

Interpretation generation is separate from quote extraction.

Important principles:

* Generate plausible readings of the supplied quotation, not claims about the author's actual intention.
* Distinguish interpretation from simple paraphrase.
* Do not force predefined interpretive lenses onto every quote.
* Several interpretations may legitimately conflict with one another.
* Clearly distinguish strongly text-supported readings from more speculative readings.
* Treat LLM output as untrusted and validate all structured responses.
* Reuse the existing local LLM infrastructure used by quote extraction unless there is a concrete reason not to.
* Runtime prompts must remain version-controlled resources.

### Quote tagging

When implementing or modifying quote tagging, or anything that consumes tags (search, adaptive visuals), first read:

`docs/features/quote-tagging.md`

Important principles:

* Tags serve two future consumers: TV search at several levels of abstraction, and adaptive visuals built from
  pre-made per-tag assets. Keep both in mind when changing facets or vocabulary rules.
* Three facets: `concept` (with `broad`/`specific` breadth), `mood`, and `motif` (concrete, drawable imagery only).
* The vocabulary is open, kept coherent by the existing-vocabulary hint in the prompt, deterministic
  normalization, and admin merges (merged tags stay as aliases). Don't bypass `TagVocabulary` when creating tags.
* Admin edits are durable: a re-run never removes admin-added tags or re-adds admin-rejected ones.

### Tag images

When implementing or modifying tag image generation, or anything that consumes `tag_images` (the collage
algorithm, serving images), first read:

`docs/features/tag-images.md`

Important principles:

* ComfyUI is reached only through the `ComfyUiClient` abstraction; its address, timeouts, image sizes and
  steps are configurable in `imageGeneration`.
* Prompts are built from version-controlled recipe resources (`resources/image-prompts/`), and workflows are
  version-controlled ComfyUI API exports (`resources/comfyui/`). Add a new version instead of editing one in place.
* Layout metadata is computed deterministically from the saved PNG, never by a model.

### Quote composition

When implementing or modifying how a TV quote screen is composed from tag images (selection, layout, text
contrast), first read:

`docs/features/quote-composition.md`

Important principles:

* The server chooses *which* images a showing gets (`server/.../composition/`); the TV app decides *where* they go and
  how the text is coloured (`platforms/tv-web/src/composition/`). Keep that split.
* Composition code is pure with an injected random source, so it is tested deterministically.
* Text must reach the contrast target against the worst part of the background under it. Prefer the gentlest means
  (tint, halo, then a solved, feathered scrim) and never a hard-edged box.

## Build commands

```powershell
.\gradlew.bat projects        # list modules
.\gradlew.bat build            # compile, test, lint, assemble everything
.\gradlew.bat :platforms:android-tv:assembleDebug
.\gradlew.bat :server:run      # run the server locally (Ctrl+C to stop)
```

(Unix/macOS: same commands with `./gradlew`.)

`admin` and `platforms/tv-web` are plain npm projects, not part of the Gradle build:

```powershell
cd admin
npm install
npm run dev      # Vite dev server on http://localhost:5173, expects the server on :8080
npm run build     # type-check (tsc -b) then production build to admin/dist
```

```powershell
cd platforms/tv-web
npm install
npm run dev      # Vite dev server, defaults to http://localhost:5173 (or next free port)
npm run build     # type-check (tsc -b) then production build to platforms/tv-web/dist
npm test          # Vitest unit tests for src/composition
```

`.\scripts\dev.ps1` starts both `:server:run` and the admin `npm run dev` in separate PowerShell
windows for local development.

## Test commands

```powershell
.\gradlew.bat test                                            # server + android-tv JVM unit tests
.\gradlew.bat :server:test
.\gradlew.bat :platforms:android-tv:testDebugUnitTest
.\gradlew.bat :platforms:android-tv:connectedDebugAndroidTest  # Compose UI test; needs a device/emulator

# single test class or method
.\gradlew.bat :server:test --tests "no.esotericgames.quotes.server.ApplicationTest"
.\gradlew.bat :platforms:android-tv:testDebugUnitTest --tests "no.esotericgames.quotes.QuoteFormattingTest"
```

## Code-style expectations

- Idiomatic Kotlin, explicit and descriptive names, no wildcard imports.
- Keep functions and composables focused; don't introduce abstractions (repositories, use cases,
  DI) for a single implementation.
- Treat compiler and lint warnings seriously — investigate and fix rather than suppress. A
  `tools:ignore` on a specific, understood false positive (documented inline) is acceptable; a
  blanket suppression is not.
- No unexplained TODOs.

## Android TV interaction requirements

- Every screen must work with a D-pad only — no touch-only affordances.
- Focusable elements need a visible focus state (see `QuoteScreen`'s border-on-focus pattern) and
  sensible initial focus (see the `FocusRequester` + `LaunchedEffect` pattern there).
- Preserve standard back-button behavior; don't intercept it without a reason.
- Keep content inside TV-safe margins (see the padding in `QuoteScreen`).

## Warnings for future sessions

- **Do not implement any of the "possible future" features** (rotation, categories, favorites,
  auth, themes, etc.) unless the user explicitly asks for that specific feature in that session.
  The admin website has grown organically with explicit requests (bulk review actions, toasts,
  table virtualization, more import sources) — that history is not license to build further admin
  features (auth, editing existing `quotes` rows, etc.) without an explicit request each time.
- **Before upgrading any dependency or plugin version, verify the new version against current
  official documentation** (developer.android.com, kotlinlang.org, ktor.io, gradle.org) rather
  than assuming — this toolchain moves fast and training-data knowledge of exact version numbers
  and compatibility goes stale quickly.
