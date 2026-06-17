package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.data.model.TimerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Duration the user must hold the dial to reset the current interval (F-020). */
private const val HOLD_TO_RESET_MS = 5_000

/** While running, the pause icon auto-hides after this delay to keep the dial clean. */
private const val ICON_AUTOHIDE_MS = 5_000L

/**
 * Circular pomodoro dial: a ring that empties as the countdown progresses, a play/pause icon in
 * the center, tap to pause/resume (US-002) and long-hold (~5s) to reset (US-007).
 *
 * @param fraction portion of the ring to draw (1 = full at start, 0 = time elapsed).
 * @param color foreground color for the ring and icon (kept white over the colored background).
 */
@Composable
fun TimerDial(
    fraction: Float,
    color: Color,
    status: TimerStatus,
    showHand: Boolean,
    onTap: () -> Unit,
    onLongHoldComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val holdProgress = remember { Animatable(0f) }
    val trackColor = color.copy(alpha = 0.25f)

    // Pause icon fades out after a few seconds while running; play icon stays put (#8).
    var iconVisible by remember { mutableStateOf(true) }
    LaunchedEffect(status) {
        if (status == TimerStatus.RUNNING) {
            iconVisible = true
            delay(ICON_AUTOHIDE_MS)
            iconVisible = false
        } else {
            iconVisible = true
        }
    }

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
            // Remaining-time sector (empties as the countdown runs). Flat caps for a sharp look.
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Thin clock hand pointing at the tip of the remaining arc, only while counting down (#1).
            if (showHand) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val handLength = size.minDimension / 2f // reach the outer edge of the ring
                val angleRad = Math.toRadians(-90.0 + 360.0 * fraction.coerceIn(0f, 1f))
                val handEnd = androidx.compose.ui.geometry.Offset(
                    center.x + (handLength * kotlin.math.cos(angleRad)).toFloat(),
                    center.y + (handLength * kotlin.math.sin(angleRad)).toFloat()
                )
                drawLine(
                    color = color,
                    start = center,
                    end = handEnd,
                    strokeWidth = strokeWidth * 0.12f,
                    cap = StrokeCap.Butt
                )
            }
            // Hold-to-reset indicator (thin outer ring filling while held).
            if (holdProgress.value > 0f) {
                val outerStroke = strokeWidth * 0.35f
                drawArc(
                    color = color.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress.value,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(outerStroke / 2f, outerStroke / 2f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - outerStroke,
                        size.height - outerStroke
                    ),
                    style = Stroke(width = outerStroke, cap = StrokeCap.Butt)
                )
            }
        }

        AnimatedVisibility(visible = iconVisible, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                imageVector = if (status == TimerStatus.RUNNING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
