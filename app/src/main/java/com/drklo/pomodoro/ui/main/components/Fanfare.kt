package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Simple, basic goal-reached celebration (PRD: "фанфары" = basic animation, no sound).
 * Shown briefly whenever [trigger] changes to a new non-zero value, then reported back through
 * [onShown] so the trigger can be cleared — otherwise entering this composition again (returning
 * from Reports, say) would replay it for the rest of the day.
 */
@Composable
fun Fanfare(
    trigger: Int,
    onShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            visible = true
            delay(VISIBLE_MS)
            visible = false
            onShown()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fanfareScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "🎉", fontSize = (BASE_FONT_SIZE * scale).sp)
        }
    }
}

private const val VISIBLE_MS = 2_000L
private const val BASE_FONT_SIZE = 96
