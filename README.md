# Pomodoro Tracker

Native Android Pomodoro timer with per-project presets, a classic circular dial, and a full
statistics module. Built in Kotlin + Jetpack Compose. Offline, no accounts, no monetization.

**[⬇ Download the APK](https://github.com/Dr-klo/pomodoro-tracker/releases/latest)** — Android 10+, ~2.7 MB, no dependencies.

> Installing takes one extra tap: the build is signed with a personal key rather than distributed
> through Google Play, so Android will warn about an unknown source and Play Protect may ask again.
> Each release page publishes the APK's SHA-256 if you want to check what you downloaded.

See [PRD.md](PRD.md) for the full product spec.

## Screenshots

| Running | Paused | Landscape |
|---|---|---|
| <img src="docs/screenshots/01-main-running.png" width="230"> | <img src="docs/screenshots/02-main-paused.png" width="230"> | <img src="docs/screenshots/03-main-landscape.png" width="230"> |

| Focus time & week journal | Month goals & trend | Per-project breakdown |
|---|---|---|
| <img src="docs/screenshots/04-reports-tomatoes.png" width="230"> | <img src="docs/screenshots/05-reports-calendar.png" width="230"> | <img src="docs/screenshots/06-reports-projects.png" width="230"> |

| Settings & presets | Russian locale |
|---|---|
| <img src="docs/screenshots/07-settings.png" width="230"> | <img src="docs/screenshots/08-localization-ru.png" width="230"> |

## Features

- **Circular dial** that empties as the countdown runs, with a clock hand and tap-to-pause /
  hold-to-reset gestures.
- **Projects** (Work, Study, Reading, …) — each with its own focus/break durations, pomodoro
  count, colors, daily goal, and long break. Swipe to switch (only while stopped/paused).
- **Single global timer** kept alive by a foreground service; controllable from the notification.
- **Phase color fills the background**, white foreground; manual phase switching (pomodoro /
  break / long break) by tapping the phase label.
- **Idle alert**: a gentle screen-color strobe every N minutes when a pomodoro/pause is left idle.
- **Always-on display**, **autostart** (pomodoros and breaks separately), **vibration**, crisp
  synthesized **sounds**, **daily goal** with a fanfare, configurable **end-of-day** time.
- **Reports** — two tabs (Tomatoes / Projects): week journal, monthly goals calendar with donut
  rings, focus-time & pomodoro-count stacked bars, per-project bars, and a distribution donut.
  Day/Week/Month aggregation, period navigation, and "vs previous period" comparisons.
- **EN / RU** localization and **System / Light / Dark** theme.

## Tech stack

- **Language:** Kotlin (2.0.x), `minSdk 29` (Android 10), `target/compileSdk 35`
- **UI:** Jetpack Compose (Material 3), custom `Canvas` charts and dial
- **Persistence:** Room (`pomodoro.db`) for projects, daily counts, and the pomodoro log;
  DataStore Preferences for settings
- **Async:** Kotlin Coroutines + Flow / StateFlow
- **DI:** manual service locator (`AppContainer`) held by the `Application`
- **Build:** Gradle (KTS) with a version catalog (`gradle/libs.versions.toml`), AGP 8.7.x, KSP

## Project structure

```
app/src/main/java/com/drklo/pomodoro/
├─ PomodoroApp.kt          Application: owns AppContainer, seeds default projects
├─ MainActivity.kt         setContent + NavHost + theme/locale wiring
├─ data/
│  ├─ AppContainer.kt      DB, repositories, and the single TimerEngine
│  ├─ LogicalDay.kt        end-of-day boundary → logical date/day key
│  ├─ model/               Project, Phase, TimerStatus, GlobalSettings, PomodoroLog, enums
│  ├─ db/                  Room entities, DAOs, AppDatabase (+ migration), mappers
│  └─ repository/          Project / Stats / Settings repositories
├─ timer/
│  ├─ TimerEngine.kt       single source of truth: state machine, ticking, idle alert
│  ├─ TimerService.kt      foreground notification + controls
│  ├─ TimerEffects.kt      sound (synthesized) + vibration
│  └─ ToneSynth.kt         runtime WAV "ding" generator
├─ ui/
│  ├─ main/                MainScreen + dial / bullets / fanfare components, MainViewModel
│  ├─ reports/             ReportsScreen, aggregation logic, chart components
│  ├─ settings/            Settings + project editor screens and view models
│  ├─ common/Stepper.kt
│  └─ theme/
└─ util/                   LocaleHelper, BatteryOptimization, context helpers
```

### Architecture

`TimerEngine` is a singleton in `AppContainer` and the single source of truth for the one global
timer. It exposes `StateFlow<TimerState>` + a `SharedFlow<TimerEvent>`; the UI (`MainViewModel` /
Compose) and `TimerService` only observe it and forward user actions. Ticking is drift-free
(computed from `SystemClock.elapsedRealtime` deadlines). Completed pomodoros persist the daily
counter and a detailed log row used by the reports.

```
[MainScreen / ViewModels] ─┐                      ┌─ [Room: projects, day_stats, pomodoro_log]
                           ├─→ [TimerEngine] ──────┤
[TimerService (foreground)]┘   (single timer)      └─ [DataStore: settings]
```

## Build & run

Requires Android 10+ (`minSdk 29`), portrait and landscape. Developed and manually verified on a
Samsung Galaxy A50 / A51 running One UI.

Android Studio is the simplest path: open the project, let the first Gradle sync run, and launch
the `app` configuration. From the command line (use `gradlew.bat` on Windows):

```bash
./gradlew :app:assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:check             # unit tests, Android Lint, detekt (production + test sources)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Android SDK location comes from `local.properties`, which is not in version control; Android
Studio writes it on first sync.

### Release builds

Signing is configured only when a `keystore.properties` is found — first in `~/.pomodoro/`, then in
the project root. The keystore and its passwords deliberately live **outside** the repository: a
signing key leaked into a public history has no revocation path, and `.gitignore` alone is a single
layer of defence.

```properties
# ~/.pomodoro/keystore.properties  — forward slashes: '\' escapes in .properties files
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the app still builds; the release variant simply comes out unsigned.
`./gradlew :app:assembleRelease` produces a ~2.7 MB APK (R8 and resource shrinking are on).

### Samsung background note

One UI can put apps into deep sleep and kill long-running timers. In-app: **Settings → Background
reliability → Disable battery optimization**. If timers still die, remove the app from
**Device care → Battery → Sleeping apps**.

## Code review

Baseline review of the whole codebase runs in fixed stages so it can be picked up one at a time:
[docs/CODE_REVIEW_PLAN.md](docs/CODE_REVIEW_PLAN.md) (how to review, linters, testability track),
[docs/REVIEW_FINDINGS.md](docs/REVIEW_FINDINGS.md) (what was found, stage statuses).

## Notes

- Detailed statistics accrue from completed pomodoros logged after install; earlier daily counts
  aren't backfilled into the timeline.
- The in-progress interval is intentionally not persisted across process death; the completed
  count for the day is restored.
