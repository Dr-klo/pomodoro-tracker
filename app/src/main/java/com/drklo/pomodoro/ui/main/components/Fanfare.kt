package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Goal-reached celebration (PRD: "фанфары" — visual only, no sound): a burst of confetti thrown up
 * from the middle of the screen, over a glow that swells and fades behind it.
 *
 * The physics is deliberately plain — one launch impulse and constant gravity — because that is
 * what reads as celebratory. Pieces spin at their own rate and fade as they fall, so the burst ends
 * by thinning out rather than by being switched off.
 *
 * Plays once per new non-zero [trigger] and reports back through [onShown] so the trigger can be
 * cleared; otherwise re-entering this composition would replay it for the rest of the day.
 */
@Composable
fun Fanfare(
    trigger: Int,
    onShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var pieces by remember { mutableStateOf(emptyList<Confetti>()) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        // A fresh burst each time, seeded from the trigger so two celebrations never match exactly.
        pieces = confettiBurst(Random(trigger))
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = BURST_MS, easing = LinearEasing))
        pieces = emptyList()
        onShown()
    }

    if (pieces.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        drawGlow(t)
        pieces.forEach { piece -> drawConfetti(piece, t) }
    }
}

/** A random number in -0.5..0.5, i.e. as likely to go one way as the other. */
private fun Random.centered(): Float = nextFloat() - HALF

/** One piece of paper: where it was thrown, how fast, how it spins and what colour it is. */
private data class Confetti(
    val angleRad: Float,
    val speed: Float,
    val spin: Float,
    val phase: Float,
    val widthFactor: Float,
    val color: Color
)

private fun confettiBurst(random: Random): List<Confetti> = List(PIECE_COUNT) {
    // Thrown upwards in a fan rather than in a full circle: a burst that also aims at the floor
    // reads as a mess, not a celebration.
    val spread = random.centered() * FAN_WIDTH_RAD
    Confetti(
        angleRad = STRAIGHT_UP_RAD + spread,
        speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED),
        spin = random.centered() * MAX_SPIN,
        phase = random.nextFloat(),
        widthFactor = MIN_WIDTH_FACTOR + random.nextFloat() * WIDTH_FACTOR_RANGE,
        color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)]
    )
}

/** A soft light behind the burst; it peaks early and is gone well before the paper lands. */
private fun DrawScope.drawGlow(t: Float) {
    val swell = (t / GLOW_PEAK).coerceAtMost(1f)
    val fade = ((1f - t) / (1f - GLOW_PEAK)).coerceIn(0f, 1f)
    val alpha = GLOW_ALPHA * swell * fade
    if (alpha <= 0f) return
    val radius = size.minDimension * (GLOW_MIN_RADIUS + GLOW_GROWTH * swell)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawConfetti(piece: Confetti, t: Float) {
    // Each piece leaves slightly after the one before it, so the burst goes up as a wave.
    val start = piece.phase * LAUNCH_STAGGER
    val local = (t - start) / (1f - start)
    if (local <= 0f) return

    val reach = size.minDimension * piece.speed
    val x = center.x + cos(piece.angleRad) * reach * local
    val y = center.y + sin(piece.angleRad) * reach * local + GRAVITY * size.height * local * local

    val side = size.minDimension * PIECE_SIZE
    val width = side * piece.widthFactor
    // Fades over the tail of its flight, so the burst thins out instead of vanishing at once.
    val alpha = ((1f - local) / FADE_TAIL).coerceIn(0f, 1f)

    rotate(degrees = piece.spin * local * FULL_TURN, pivot = Offset(x, y)) {
        drawRect(
            color = piece.color.copy(alpha = alpha),
            topLeft = Offset(x - width / 2f, y - side / 2f),
            size = Size(width, side)
        )
    }
}

@Suppress("MagicNumber") // A palette: the numbers are the colours.
private val CONFETTI_COLORS = listOf(
    Color(0xFFFFD54F),
    Color(0xFFFF7043),
    Color(0xFF4FC3F7),
    Color(0xFF81C784),
    Color(0xFFBA68C8),
    Color(0xFFFFFFFF)
)

/** Screen coordinates grow downwards, so "up" is a quarter turn anticlockwise. */
private val STRAIGHT_UP_RAD = (-PI / 2).toFloat()

private const val HALF = 0.5f
private const val BURST_MS = 1_800
private const val PIECE_COUNT = 90
private const val FAN_WIDTH_RAD = 2.2f
private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 1.15f
private const val MAX_SPIN = 3f
private const val FULL_TURN = 360f
private const val GRAVITY = 1.15f
private const val PIECE_SIZE = 0.022f
private const val MIN_WIDTH_FACTOR = 0.6f
private const val WIDTH_FACTOR_RANGE = 0.8f
private const val LAUNCH_STAGGER = 0.25f
private const val FADE_TAIL = 0.45f
private const val GLOW_ALPHA = 0.5f
private const val GLOW_PEAK = 0.25f
private const val GLOW_MIN_RADIUS = 0.25f
private const val GLOW_GROWTH = 0.45f
