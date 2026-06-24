# Pomodoro Tracker

Native Android Pomodoro timer with per-project presets, a classic circular dial, and a full
statistics module. Built in Kotlin + Jetpack Compose. Offline, no accounts, no monetization.

See [PRD.md](PRD.md) for the full product spec.

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

Android Studio is the simplest path (Open project → first Gradle sync downloads Gradle and
generates the wrapper jar if missing → Run the `app` config). The command line works too — no
separate JDK/SDK install needed, reuse the ones Android Studio bundles.

Target device: Samsung Galaxy A50/A51 (Android 10+).

### Build, commit & deploy from the command line (Windows / PowerShell)

Android Studio's JBR is the JDK; the SDK ships `adb`. On this machine:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# Build the debug APK (SDK path comes from local.properties, not in git)
.\gradlew.bat :app:assembleDebug
# -> app\build\outputs\apk\debug\app-debug.apk

# Deploy to the connected device (check it's there first)
& $adb devices
& $adb install -r app\build\outputs\apk\debug\app-debug.apk

# Commit (multi-line message via a file — PowerShell splits `-m` here-strings)
git commit -F path\to\message.txt
```

### Samsung background note

One UI can put apps into deep sleep and kill long-running timers. In-app: **Settings → Background
reliability → Disable battery optimization**. If timers still die, remove the app from
**Device care → Battery → Sleeping apps**.

## Notes

- Detailed statistics accrue from completed pomodoros logged after install; earlier daily counts
  aren't backfilled into the timeline.
- The in-progress interval is intentionally not persisted across process death; the completed
  count for the day is restored.
