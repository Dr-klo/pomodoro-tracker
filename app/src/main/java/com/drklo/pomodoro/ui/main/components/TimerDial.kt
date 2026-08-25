package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.data.model.TimerStatus
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2

/** While running, the pause icon auto-hides after this delay to keep the dial clean. */
private const val ICON_AUTOHIDE_MS = 5_000L

/**
 * Circular pomodoro dial: a ring that empties as the countdown progresses, a play/pause icon in
 * the center, tap to pause/resume (US-002) and drag around the ring to scrub the remaining time
 * (fast-forward a phase started late).
 *
 * @param fraction portion of the ring to draw (1 = full at start, 0 = time elapsed).
 * @param color foreground color for the ring and icon (kept white over the colored background).
 * @param onSeek reports a new remaining fraction (0..1) while the user drags the ring.
 */
@Composable
fun TimerDial(
    fraction: Float,
    color: Color,
    status: TimerStatus,
    showHand: Boolean,
    onTap: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tapLabel: String? = null
) {
    val trackColor = color.copy(alpha = 0.25f)
    // Latest values captured for the gesture handlers, which outlive a single composition.
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnSeek by rememberUpdatedState(onSeek)

    // While dragging, render this scrubbed value instead of the (lagging) prop; null when idle.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownFraction = dragFraction ?: fraction

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
            // A real clickable rather than detectTapGestures: raw pointer input is invisible to
            // accessibility services, so the app's main control could not be reached or activated
            // by a screen reader (F-R0-01). Indication is suppressed to keep the dial's look, and a
            // drag is not mistaken for a click, so scrubbing still works.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = tapLabel
            ) { onTap() }
            .pointerInput(Unit) {
                // Drag anywhere on the dial to rotate the remaining-time sector. We accumulate the
                // angular delta around the center (robust to the wrap at 12 o'clock) and translate
                // a full turn into the whole interval. Clockwise (extending the arc) adds time.
                detectDragGestures(
                    onDragStart = { dragFraction = currentFraction },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null }
                ) { change, _ ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val prev = change.previousPosition
                    val cur = change.position
                    val anglePrev = atan2((prev.y - cy).toDouble(), (prev.x - cx).toDouble())
                    val angleCur = atan2((cur.y - cy).toDouble(), (cur.x - cx).toDouble())
                    var delta = angleCur - anglePrev
                    // Normalize into (-PI, PI] so a step across 12 o'clock stays a small delta.
                    if (delta > PI) delta -= 2 * PI
                    if (delta < -PI) delta += 2 * PI
                    val next = ((dragFraction ?: currentFraction) + (delta / (2 * PI)).toFloat())
                        .coerceIn(0f, 1f)
                    dragFraction = next
                    currentOnSeek(next)
                    change.consume()
                }
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
                sweepAngle = 360f * shownFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Thin clock hand pointing at the tip of the remaining arc, while counting down (#1) or
            // while scrubbing, so the user can see where they're dragging the sector tip.
            if (showHand || dragFraction != null) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val handLength = size.minDimension / 2f // reach the outer edge of the ring
                val angleRad = Math.toRadians(-90.0 + 360.0 * shownFraction.coerceIn(0f, 1f))
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
