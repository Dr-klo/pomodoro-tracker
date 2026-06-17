package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.data.model.TimerStatus
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Duration the user must hold the dial to reset the current interval (F-020). */
private const val HOLD_TO_RESET_MS = 5_000

/**
 * Circular pomodoro dial: a track ring with a filling sector for progress, a play/pause icon in
 * the center, tap to pause/resume (US-002) and long-hold (~5s) to reset (US-007).
 */
@Composable
fun TimerDial(
    progress: Float,
    accentColor: Color,
    status: TimerStatus,
    onTap: () -> Unit,
    onLongHoldComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val holdProgress = remember { Animatable(0f) }
    val trackColor = accentColor.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(status) {
                detectTapGestures(
                    onTap = { onTap() },
                    onPress = {
                        val holdJob = scope.launch {
                            holdProgress.snapTo(0f)
                            holdProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(HOLD_TO_RESET_MS, easing = LinearEasing)
                            )
                            if (isActive) onLongHoldComplete()
                        }
                        tryAwaitRelease()
                        holdJob.cancel()
                        scope.launch { holdProgress.snapTo(0f) }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.08f
            val inset = strokeWidth / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth,
                size.height - strokeWidth
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            // Background track.
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth)
            )
            // Progress sector.
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Hold-to-reset indicator (thin outer ring filling while held).
            if (holdProgress.value > 0f) {
                val outerStroke = strokeWidth * 0.35f
                drawArc(
                    color = accentColor.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress.value,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(outerStroke / 2f, outerStroke / 2f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - outerStroke,
                        size.height - outerStroke
                    ),
                    style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                )
            }
        }

        Icon(
            imageVector = if (status == TimerStatus.RUNNING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(72.dp)
        )
    }
}
