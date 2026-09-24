# TV Quotes

An app for TVs that displays interesting quotes, targeting multiple TV platforms. Frontends live
under `platforms/`, one directory per TV platform.

## Modules

- **`platforms/android-tv`** — Native Android TV application (Kotlin, Jetpack Compose, Compose for
  TV). Single activity, one placeholder screen. Package: `no.esotericgames.quotes`.
- **`platforms/tv-web`** — React + TypeScript + Vite frontend for web-based TV platforms
  (browser-testable, webOS-packageable). Not a Gradle module.
- **`server`** — Kotlin/JVM Ktor server (Netty engine). Package: `no.esotericgames.quotes.server`.

## Prerequisites

- **Android Studio**, current stable channel (this project was built against Android Studio
  "Quail 3" 2026.1.3). An Android TV system image, installed via the SDK Manager or accepted
  automatically when you create a TV emulator.
- No local Gradle or JDK installation is required — the project uses the Gradle Wrapper
  (`gradlew` / `gradlew.bat`) and a Gradle Java toolchain (see below), both of which provision
  themselves.
- An internet connection the first time you build, so Gradle can download the wrapper
  distribution and dependencies.

## Java toolchain

Both modules declare `kotlin { jvmToolchain(21) }`, so Gradle compiles with JDK 21 regardless of
which JDK launches the Gradle daemon itself. If JDK 21 isn't already installed, Gradle's Foojay
toolchain resolver (configured in `settings.gradle.kts`) downloads one automatically — no manual
JDK setup required.

## Opening the project in Android Studio

1. **File → Open** and select the repository root (`quote-viewer/`).
2. Let Android Studio sync Gradle. This uses `gradlew`/`gradlew.bat`, so no separately installed
   Gradle is involved.
3. Once synced, both `platforms/android-tv` and `server` appear as modules in the project view.

## Creating or selecting an Android TV emulator

1. **Tools → Device Manager → Create device**.
2. Select the **TV** category (e.g. "Television (1080p)") and pick a system image (a recent
   stable Google TV or Android TV image).
3. Finish the wizard and start the emulator from Device Manager.

## Running the TV app

- From Android Studio: select the `android-tv` run configuration, choose the TV emulator (or a
  physical Android TV device with USB debugging enabled), and click **Run**.
- From the command line:

  ```powershell
  .\gradlew.bat :platforms:android-tv:installDebug
  ```

  ```bash
  ./gradlew :platforms:android-tv:installDebug
  ```

  Then launch "TV Quotes" from the emulator/device's leanback launcher.

Navigate with the D-pad (arrow keys in the emulator) — the placeholder screen's quote card is
focusable and shows a focus outline.

## Starting the Ktor server

```powershell
.\gradlew.bat :server:run
```

```bash
./gradlew :server:run
```

This starts Netty on port 8080 by default. Verify it's up:

```powershell
curl.exe http://localhost:8080/health
curl.exe http://localhost:8080/api/quotes
```

Stop it with Ctrl+C.

### Host and port

Both are externally configurable via `server/src/main/resources/application.conf`, which reads
the `PORT` and `HOST` environment variables (falling back to `8080` and `0.0.0.0`):

```powershell
$env:PORT = "9090"; .\gradlew.bat :server:run
```

## Running all tests

```powershell
.\gradlew.bat test
```

```bash
./gradlew test
```

This runs the server's Ktor `testApplication` tests and the TV app's JVM unit test. The TV app
also has one Compose UI test under `platforms/android-tv/src/androidTest`, which requires a
connected device/emulator:

```powershell
.\gradlew.bat :platforms:android-tv:connectedDebugAndroidTest
```

## Building everything from the command line

```powershell
.\gradlew.bat build
```

```bash
./gradlew build
```

This compiles both modules, runs unit tests and lint, and assembles a debug APK
(`platforms/android-tv/build/outputs/apk/debug/`) plus a runnable server jar
(`server/build/libs/server-all.jar`).

## Where dependency versions are managed

All plugin and library versions live in `gradle/libs.versions.toml` (the Gradle version catalog).
Module `build.gradle.kts` files reference catalog entries (`libs.someLibrary`) rather than
hard-coded version strings.

## Where the future server base URL is configured

`platforms/android-tv/build.gradle.kts` sets a single `buildConfigField` named `SERVER_BASE_URL`
(`no.esotericgames.quotes.BuildConfig.SERVER_BASE_URL`) as the one place to change it later. It
currently defaults to `http://10.0.2.2:8080` and is **not** wired to any network call yet.

## Emulator networking: 10.0.2.2 vs. localhost

The standard Android Emulator runs the app inside its own virtual network, separate from your
development machine. Inside the emulator, `localhost` refers to the emulator itself, **not** your
computer. To reach a server running on your development machine (such as the Ktor server above),
the emulator provides a special alias:

```
10.0.2.2
```

This is why `SERVER_BASE_URL` defaults to `http://10.0.2.2:8080` rather than
`http://localhost:8080`.

This address is specific to the standard Android Emulator (Android Virtual Device, "AVD").
It generally does **not** apply to:

- **Physical Android TV devices** — use your development machine's actual LAN IP address instead
  (e.g. `192.168.x.x`), and ensure both devices are on the same network.
- **Third-party emulators** (e.g. Genymotion) — these typically use a different alias
  (Genymotion uses `10.0.3.2`).
- **Containers or remote development environments** — networking depends entirely on how the
  container/VM is configured; there is no universal alias.

Check the current environment before assuming `10.0.2.2` will work.
