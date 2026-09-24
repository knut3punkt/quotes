# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

## The product vision

TV Quotes: An app for TVs (initially webOS and Android TV) that displays interesting quotes. The two
key features are visually and intelectually pleasing quote screens, and intelligent voice search
that takes various interpretations of quotes into account to provide relevant and thought
provoking results.

## Current scope

The TV app is still a fresh foundation. There is **no** authentication, dependency injection,
navigation framework, networking layer, or background work in `tv-app`, and none of that should be
added speculatively. See the "possible future features" list in the original project brief for
context on where this is headed — none of it is implemented, and none of it should be started
without an explicit request. The `admin` website (see below) is the first concrete step into that
list and exists because it was explicitly requested — don't take it as license to build out the
rest of the list unprompted.

## Module responsibilities

- **`tv-app`** (`no.esotericgames.quotes`) — Native Android TV app. Single activity
  (`MainActivity`), one composable screen (`QuoteScreen`) showing a hard-coded `Quote`. TV theming
  lives in `theme/` (`Theme.kt`, `Color.kt`, `Type.kt`) and uses `androidx.tv.material3`, not the
  phone `androidx.compose.material3` artifact.
- **`server`** (`no.esotericgames.quotes.server`) — Ktor/Netty server. `Application.kt` is the
  entry point (`EngineMain`), which wires up every import service and calls `configureRouting`;
  `Routing.kt` defines routes; `Models.kt` holds the `@Serializable` response types and the
  hard-coded `/api/quotes` sample quotes (the TV app's real feed, still unimplemented). `db/` holds
  the Exposed table definitions and Flyway-migrated PostgreSQL schema (`authors`, `source_types`,
  `sources`, `quotes`, `tags`, `imported_quotes`). Each importable source has its own package
  (`wikiquote/`, `bible/`, `quran/`, `bhagavadgita/`, `dhammapada/`, `taote/`) built on the shared
  staging pipeline in `importing/` — see "Quote import pipeline" below. `wikidata/` enriches
  existing `authors` rows (birth/death year, QID) from Wikidata rather than importing quotes.
  `admin/` (package, not to be confused with the top-level `admin` frontend project) holds the
  admin API — DTOs and the service backing the `/admin/*` routes used to review and approve staged
  imports into real `authors`/`quotes` rows, manage `sources`, and enrich authors. See
  `server/CLAUDE.md` for the local Postgres access/migration convention.
- **`admin`** — React 19 + TypeScript + Vite admin website (not a Gradle module — a separate npm
  project), using Tailwind v4, shadcn/ui-style components (`src/components/ui/`, radix-nova style,
  see `components.json`) built on Radix primitives, and oxlint. Talks to the `server`'s `/admin/*`
  routes over plain `fetch` (`src/api.ts`), no auth yet. Lets a human filter `imported_quotes` by
  processing status/confidence/provider/search, review individually or in bulk (status
  update/delete), approve into `authors`/`quotes` rows, trigger scripture/Wikiquote imports
  (`QuickImportsSection`, `ImportPage`), and trigger Wikidata author enrichment. The imported-quotes
  table is virtualized (`@tanstack/react-virtual`) for large review queues.

## Key architectural decisions

- **AGP 9's built-in Kotlin support** compiles `tv-app`; the standalone
  `org.jetbrains.kotlin.android` plugin is intentionally *not* applied there. `server` uses the
  standard `org.jetbrains.kotlin.jvm` plugin, since built-in Kotlin only covers Android modules.
- The Ktor Gradle plugin (`io.ktor.plugin`) is applied in `server`; it implicitly manages the Ktor
  BOM, so `io.ktor:*` dependencies are declared without explicit versions.
- No ViewModel in `tv-app` — there's no state or lifecycle need yet. Add one only when a concrete
  need appears, not preemptively.
- `SERVER_BASE_URL` is a single `buildConfigField` in `tv-app/build.gradle.kts` — the one place to
  point the app at a server later. It is not wired to a network call yet.

## Quote import pipeline

Every source-specific importer (`wikiquote/`, `bible/`, `quran/`, `bhagavadgita/`, `dhammapada/`,
`taote/`) follows the same two-layer shape, and a new source should too:

- A **`*Client`** (e.g. `WikiquoteClient`, `BibleClient`) fetches/parses raw content from its
  origin — a live HTTP source for Wikiquote/Bible/Quran/Bhagavad Gita/Dhammapada, or a bundled
  resource file under `server/src/main/resources/scripture/` for sources with a fixed public-domain
  text (Tao Te Ching) where vendoring avoids a live dependency.
- A **`*ImportService`** turns that raw content into `StagedQuoteCandidate`s and hands each one to
  the shared `importing/` package, which every importer must go through rather than inserting into
  `ImportedQuotes` directly:
  - `findOrCreateSource` (`SourceRegistry.kt`) dedupes `sources` rows by case-insensitive
    title + type + translation, so re-running an importer doesn't create duplicate source rows.
  - `stageQuote` (`ImportStaging.kt`) inserts into `imported_quotes` and applies two dedup tiers: an
    exact-after-normalization match (against both `quotes` and other `imported_quotes` rows) that
    immediately marks the row `duplicate`, and a `pg_trgm` fuzzy-similarity match that only leaves a
    `possible_duplicate_of_id` hint for a human reviewer.
  - `TextNormalization.kt` defines the normalization used for both dedup tiers.
  - `ScriptureImportResult` is the shared response shape (`sourceId`, `quotesInserted`,
    `quotesSkippedAsDuplicate`, `quotesFailedToFetch`) returned by the whole-canon and seed-list
    scripture importers.
- Whole-canon importers (Tao Te Ching, Bhagavad Gita, Dhammapada) enumerate their entire source and
  never have unresolvable entries; seed-list importers (Bible, Quran) walk a bundled list of
  references (`bible-kjv-seed-refs.json`, `quran-pickthall-seed-refs.json`) that a live API call can
  fail to resolve, which is what `quotesFailedToFetch` tracks.

## Dependency and version-management conventions

- All versions live in `gradle/libs.versions.toml`. Never hard-code a version string in a module
  `build.gradle.kts`; add or reuse a catalog entry instead.
- No dynamic versions (`+`) and no unversioned plugin/dependency declarations, except where a BOM
  or the Ktor Gradle plugin intentionally supplies the version.
- Compose libraries covered by the Compose BOM (`androidx.compose:compose-bom`) get their version
  from the BOM, not the catalog. Compose for TV (`androidx.tv:tv-material`,
  `androidx.tv:tv-foundation`) is **not** covered by that BOM and is pinned explicitly.

## Build commands

```powershell
.\gradlew.bat projects        # list modules
.\gradlew.bat build            # compile, test, lint, assemble everything
.\gradlew.bat :tv-app:assembleDebug
.\gradlew.bat :server:run      # run the server locally (Ctrl+C to stop)
```

(Unix/macOS: same commands with `./gradlew`.)

`admin` is a plain npm project, not part of the Gradle build:

```powershell
cd admin
npm install
npm run dev      # Vite dev server on http://localhost:5173, expects the server on :8080
npm run build     # type-check (tsc -b) then production build to admin/dist
npm run lint      # oxlint
```

`.\scripts\dev.ps1` starts both `:server:run` and the admin `npm run dev` in separate PowerShell
windows for local development.

## Test commands

```powershell
.\gradlew.bat test                              # server + tv-app JVM unit tests
.\gradlew.bat :server:test
.\gradlew.bat :tv-app:testDebugUnitTest
.\gradlew.bat :tv-app:connectedDebugAndroidTest  # Compose UI test; needs a device/emulator

# single test class or method
.\gradlew.bat :server:test --tests "no.esotericgames.quotes.server.ApplicationTest"
.\gradlew.bat :tv-app:testDebugUnitTest --tests "no.esotericgames.quotes.QuoteFormattingTest"
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
