package com.drklo.pomodoro.ui.main

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.timer.TimerState
import com.drklo.pomodoro.timer.formatMmSs
import com.drklo.pomodoro.ui.main.components.Fanfare
import com.drklo.pomodoro.ui.main.components.PhaseLabel
import com.drklo.pomodoro.ui.main.components.SessionBullets
import com.drklo.pomodoro.ui.main.components.TimerDial
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.timerState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fanfareTrigger by viewModel.fanfareTrigger.collectAsStateWithLifecycle()

    // Always-on display (F-011): keep the screen awake while the timer is active or awaiting.
    val keepOn = settings.alwaysOnDisplay && (state.status != TimerStatus.IDLE || state.awaitingNext)
    val view = LocalView.current
    LaunchedEffect(keepOn) { view.keepScreenOn = keepOn }

    if (projects.isEmpty()) return

    val isRunning = state.status == TimerStatus.RUNNING
    val canSwipe = state.status == TimerStatus.IDLE || state.status == TimerStatus.PAUSED

    val pagerState = rememberPagerState(initialPage = viewModel.indexOfActiveProject()) { projects.size }

    // Apply carousel selection once a page settles (F-008).
    LaunchedEffect(pagerState.settledPage, projects.size) {
        viewModel.onSelectProject(pagerState.settledPage)
    }

    val isLandscape =
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canSwipe,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val project = projects[page]
            val isActive = project.id == state.project?.id
            ProjectPage(
                project = project,
                state = state,
                isActive = isActive,
                isRunning = isRunning,
                isLandscape = isLandscape,
                onTap = { if (isActive) viewModel.onPlayPause() },
                onReset = viewModel::onReset,
                onChangePhase = viewModel::onChangePhase
            )
        }

        // Burger menu — hidden during the active session for minimalism (F-019).
        if (!isRunning) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_settings))
            }
        }

        Fanfare(trigger = fanfareTrigger, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ProjectPage(
    project: Project,
    state: TimerState,
    isActive: Boolean,
    isRunning: Boolean,
    isLandscape: Boolean,
    onTap: () -> Unit,
    onReset: () -> Unit,
    onChangePhase: () -> Unit
) {
    // The page reflects live state only for the active project; others show an idle preview.
    val phase = if (isActive) state.phase else Phase.POMODORO
    val status = if (isActive) state.status else TimerStatus.IDLE
    val progress = if (isActive) state.progress else 0f
    val filledBullets = if (isActive) state.completedInSession else 0

    val flip = isActive && state.awaitingNext && state.idleAlertActive
    val accent: Color = when {
        flip -> Color(if (phase == Phase.POMODORO) project.breakColor else project.pomodoroColor)
        else -> Color(project.colorFor(phase))
    }.takeIf { it.alpha > 0f } ?: DefaultPomodoroColor

    val phaseName = stringResource(
        when (phase) {
            Phase.POMODORO -> R.string.phase_pomodoro
            Phase.SHORT_BREAK -> R.string.phase_short_break
            Phase.LONG_BREAK -> R.string.phase_long_break
        }
    )
    val minutes = project.durationSecondsFor(phase) / 60
    val labelText = if (isActive && status != TimerStatus.IDLE) {
        formatMmSs(state.remainingSeconds)
    } else {
        stringResource(R.string.phase_label_format, phaseName, minutes, stringResource(R.string.minutes_short))
    }
    val canChangePhase = isActive && status != TimerStatus.RUNNING
    val showReset = isActive && status == TimerStatus.PAUSED

    val dial = @Composable { dialModifier: Modifier ->
        TimerDial(
            progress = progress,
            accentColor = accent,
            status = status,
            onTap = onTap,
            onLongHoldComplete = onReset,
            modifier = dialModifier
        )
    }

    val details = @Composable {
        SessionBullets(filled = filledBullets, total = project.pomodorosPerSession, color = accent)
        Spacer(Modifier.height(12.dp))
        Text(
            text = project.name,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        PhaseLabel(
            text = labelText,
            color = accent,
            changeEnabled = canChangePhase,
            onChangePhase = onChangePhase
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
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
                modifier = Modifier.fillMaxSize().padding(24.dp),
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clickable { onReset() }
            )
        }
    }
}
