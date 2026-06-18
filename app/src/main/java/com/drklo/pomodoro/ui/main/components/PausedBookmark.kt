package com.drklo.pomodoro.ui.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * "Bookmark" tab for a paused session shown while browsing other projects: a rounded tab stuck to
 * the right edge with the paused project's color and remaining time. Tapping returns to it.
 * Shakes when [shakeTrigger] changes (a blocked attempt to start another pomodoro).
 */
@Composable
fun PausedBookmark(
    color: Color,
    timeText: String,
    shakeTrigger: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            repeat(3) {
                offsetX.animateTo(12f, tween(45))
                offsetX.animateTo(-12f, tween(45))
            }
            offsetX.animateTo(0f, tween(45))
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
        color = color,
        shadowElevation = 6.dp,
        modifier = modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeText,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}
