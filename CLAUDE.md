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
  navigation with wrap-around. `src/platform/` provides `webos`/`browser` detection for future
  platform-specific branching.
- **`server`** (`no.esotericgames.quotes.server`) — Ktor/Netty server. `Application.kt` is the
  entry point (`EngineMain`), `Routing.kt` defines routes, `Models.kt` holds the `@Serializable`
  response types and the hard-coded sample quotes. `PublicQuoteService.kt` backs the public
  `GET /api/quotes/random` endpoint (random verified quotes with author/source, for TV frontends).
  `db/` holds the Exposed table definitions and Flyway-migrated PostgreSQL schema (`authors`,
  `sources`, `quotes`, `tags`, `imported_quotes`). `wikiquote/` is the Wikiquote importer, which
  stages results in `imported_quotes`. `admin/` (package, not to be confused with the top-level
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
