# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

## Product overview

TV Quotes: an Android TV app that will eventually display quotes fetched from a companion Kotlin
server. Today it is boilerplate only — a placeholder screen and two static server endpoints, not
yet connected to each other.

## Current scope

This is a fresh foundation. There is **no** persistence, authentication, dependency injection,
navigation framework, networking layer, background work, or admin UI, and none of that should be
added speculatively. See the "possible future features" list in the original project brief for
context on where this is headed — none of it is implemented, and none of it should be started
without an explicit request.

## Module responsibilities

- **`tv-app`** (`no.esotericgames.quotes`) — Native Android TV app. Single activity
  (`MainActivity`), one composable screen (`QuoteScreen`) showing a hard-coded `Quote`. TV theming
  lives in `theme/` (`Theme.kt`, `Color.kt`, `Type.kt`) and uses `androidx.tv.material3`, not the
  phone `androidx.compose.material3` artifact.
- **`server`** (`no.esotericgames.quotes.server`) — Ktor/Netty server. `Application.kt` is the
  entry point (`EngineMain`), `Routing.kt` defines routes, `Models.kt` holds the `@Serializable`
  response types and the hard-coded sample quotes.

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
  remote admin, themes, etc.) unless the user explicitly asks for that specific feature in that
  session.
- **Before upgrading any dependency or plugin version, verify the new version against current
  official documentation** (developer.android.com, kotlinlang.org, ktor.io, gradle.org) rather
  than assuming — this toolchain moves fast and training-data knowledge of exact version numbers
  and compatibility goes stale quickly.
