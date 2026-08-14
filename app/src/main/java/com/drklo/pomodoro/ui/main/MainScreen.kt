package com.drklo.pomodoro.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.timer.TimerState
import com.drklo.pomodoro.timer.formatMmSs
import com.drklo.pomodoro.ui.main.components.Fanfare
import com.drklo.pomodoro.ui.main.components.PausedBookmark
import com.drklo.pomodoro.ui.main.components.SessionBullets
import com.drklo.pomodoro.ui.main.components.TimerDial
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import com.drklo.pomodoro.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Hide the system bars after this much inactivity on the main screen. */
private const val IDLE_HIDE_MS = 3_000L

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.timerState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fanfareTrigger by viewModel.fanfareTrigger.collectAsStateWithLifecycle()

    // Roll session bullets onto the current logical day whenever the app comes to the foreground,
    // so yesterday's progress doesn't linger when the process survives past the day-end boundary.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

    // Always-on display (F-011): keep the screen awake while the timer is active or awaiting.
    val keepOn = settings.alwaysOnDisplay && (state.status != TimerStatus.IDLE || state.awaitingNext)
    val view = LocalView.current
    LaunchedEffect(keepOn) { view.keepScreenOn = keepOn }

    // Auto-hide system bars after inactivity so the Samsung nav buttons stop overlapping the
    // top-right icons (especially in landscape). Any touch reveals them; edge-swipe shows transiently.
    val window = view.context.findActivity()?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    var barsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionTick) {
        barsVisible = true
        delay(IDLE_HIDE_MS)
        barsVisible = false
    }
    LaunchedEffect(barsVisible, insetsController) {
        val bars = WindowInsetsCompat.Type.systemBars()
        if (barsVisible) insetsController?.show(bars) else insetsController?.hide(bars)
    }
    DisposableEffect(insetsController) {
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Deleting the last project is refused, so an empty list only happens in the sliver before the
    // first-run seeding lands. Even then the screen must stay usable: a blank frame with no controls
    // used to be a dead end, escapable only by restarting the app.
    if (projects.isEmpty()) {
        EmptyProjects(onOpenSettings = onOpenSettings)
        return
    }

    val isRunning = state.status == TimerStatus.RUNNING
    val canSwipe = state.status == TimerStatus.IDLE || state.status == TimerStatus.PAUSED

    // Infinite carousel: a huge virtual page count starting in the middle; the real project index
    // is page % size, so swiping past the ends wraps around instead of hitting a boundary.
    val infinite = projects.size > 1
    val pageCount = if (infinite) Int.MAX_VALUE else 1
    val startPage = remember(projects.size) {
        if (infinite) {
            (Int.MAX_VALUE / 2) / projects.size * projects.size + viewModel.indexOfActiveProject()
        } else 0
    }
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }

    // React only to genuine user-driven page settles (drop the initial value so returning from
    // another screen does not reset a paused timer) — F-008, fix for settings round-trip.
    LaunchedEffect(pagerState, projects.size) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page -> viewModel.onSelectProject(page % projects.size) }
    }

    val isLandscape =
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    // Paused session "parked" on its project; shown as a bookmark while browsing other projects.
    val pausedProject = if (state.status == TimerStatus.PAUSED) state.project else null
    val viewedProject = projects.getOrNull(pagerState.currentPage % projects.size)
    val showBookmark = pausedProject != null && viewedProject?.id != pausedProject.id
    var shakeTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        interactionTick++
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canSwipe,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val project = projects[page % projects.size]
            val isActive = project.id == state.project?.id
            ProjectPage(
                project = project,
                state = state,
                isActive = isActive,
                isLandscape = isLandscape,
                holdFinishedColor = settings.holdFinishedPhaseColor,
                // Tapping a different project while one is paused is blocked; nudge the bookmark.
                onTap = {
                    if (isActive) viewModel.onPlayPause()
                    else if (pausedProject != null) shakeTrigger++
                },
                onReset = { if (isActive) viewModel.onReset() },
                onSeek = { if (isActive) viewModel.onSeek(it) },
                onChangePhase = { if (isActive) viewModel.onChangePhase() }
            )
        }

        // Bookmark for the paused project (bottom-right, sticks out from the edge).
        // `showBookmark` already implies a non-null paused project, hence no second check.
        if (showBookmark) {
            PausedBookmark(
                color = Color(pausedProject.pomodoroColor),
                timeText = formatMmSs(state.remainingSeconds),
                shakeTrigger = shakeTrigger,
                onClick = {
                    val pausedIndex = projects.indexOfFirst { it.id == pausedProject.id }
                    if (pausedIndex >= 0) {
                        val base = pagerState.currentPage - (pagerState.currentPage % projects.size)
                        scope.launch { pagerState.animateScrollToPage(base + pausedIndex) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
            )
        }

        // Reports + burger — hidden during the active session for minimalism (F-019).
        if (!isRunning) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(4.dp)
            ) {
                IconButton(onClick = onOpenReports) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = stringResource(R.string.cd_reports),
                        tint = Color.White
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = Color.White
                    )
                }
            }
        }

        Fanfare(trigger = fanfareTrigger, modifier = Modifier.fillMaxSize())
    }
}

/** Placeholder shown while there is no project to draw — never a blank, uncontrollable screen. */
@Composable
private fun EmptyProjects(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = stringResource(R.string.empty_no_projects),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center)
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(4.dp)
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProjectPage(
    project: Project,
    state: TimerState,
    isActive: Boolean,
    isLandscape: Boolean,
    holdFinishedColor: Boolean,
    onTap: () -> Unit,
    onReset: () -> Unit,
    onSeek: (Float) -> Unit,
    onChangePhase: () -> Unit
) {
    // The page reflects live state only for the active project; others show an idle preview.
    val phase = if (isActive) state.phase else Phase.POMODORO
    val status = if (isActive) state.status else TimerStatus.IDLE
    val fraction =
        if (isActive && state.totalSeconds > 0) state.remainingSeconds.toFloat() / state.totalSeconds
        else 1f
    val filledBullets = if (isActive) state.completedInSession else 0

    // Phase color is the BACKGROUND; foreground stays white (#3).
    // While awaiting the next phase (a phase finished but isn't auto-started) keep showing the COLOR
    // of the JUST-FINISHED phase, so peripheral vision doesn't read the screen as a new phase already
    // running. state.phase is already the NEXT phase here, so the finished color is the opposite one.
    // The idle-alert strobe (#6) alternates against that base; idleAlertActive is only ever true while
    // stalled (paused or awaiting), so this is safe.
    val phaseColor = Color(project.colorFor(phase))
    val oppositeColor =
        Color(if (phase == Phase.POMODORO) project.breakColor else project.pomodoroColor)
    val awaiting = holdFinishedColor && isActive && state.awaitingNext && status == TimerStatus.IDLE
    val flip = isActive && state.idleAlertActive
    val bgColor: Color = when {
        awaiting -> if (flip) phaseColor else oppositeColor // hold finished color; strobe to upcoming
        flip -> oppositeColor
        else -> phaseColor
    }.takeIf { it.alpha > 0f } ?: DefaultPomodoroColor
    val fg = Color.White

    val phaseName = stringResource(
        when (phase) {
            Phase.POMODORO -> R.string.phase_pomodoro
            Phase.SHORT_BREAK -> R.string.phase_short_break
            Phase.LONG_BREAK -> R.string.phase_long_break
        }
    )
    val minutes = project.durationSecondsFor(phase) / 60
    val timeText = if (isActive && status != TimerStatus.IDLE) {
        formatMmSs(state.remainingSeconds)
    } else {
        "$minutes ${stringResource(R.string.minutes_short)}"
    }
    val canChangePhase = isActive && status != TimerStatus.RUNNING
    val showReset = isActive && status == TimerStatus.PAUSED

    val dial = @Composable { dialModifier: Modifier ->
        TimerDial(
            fraction = fraction,
            color = fg,
            status = status,
            showHand = status == TimerStatus.RUNNING,
            onTap = onTap,
            onSeek = onSeek,
            modifier = dialModifier
        )
    }

    val details = @Composable {
        SessionBullets(filled = filledBullets, total = project.pomodorosPerSession, color = fg)
        Spacer(Modifier.height(12.dp))
        Text(text = project.name, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = fg)
        Spacer(Modifier.height(6.dp))
        // Phase type — always visible so the user knows pomodoro vs break (#7); tap to change (F-021).
        Text(
            text = phaseName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = fg.copy(alpha = 0.9f),
            modifier = if (canChangePhase) Modifier.clickable { onChangePhase() } else Modifier
        )
        Spacer(Modifier.height(2.dp))
        Text(text = timeText, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                dial(Modifier.weight(1f).fillMaxHeight().padding(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { details() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                dial(Modifier.fillMaxWidth().widthIn(max = 360.dp).padding(8.dp))
                Spacer(Modifier.height(32.dp))
                details()
            }
        }

        // Small reset label at the bottom, visible only while paused (F-020, reference UX).
        if (showReset) {
            Text(
                text = stringResource(R.string.action_reset),
                color = fg.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clickable { onReset() }
            )
        }
    }
}
