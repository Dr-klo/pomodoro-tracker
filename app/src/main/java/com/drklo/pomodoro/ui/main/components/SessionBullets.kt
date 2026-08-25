package com.drklo.pomodoro.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Row of bullets showing session progress (US-007): [total] dots, [filled] of them solid.
 */
@Composable
fun SessionBullets(
    filled: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(total) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(
                        if (isFilled) {
                            Modifier.background(color)
                        } else {
                            Modifier.border(width = 1.5.dp, color = color.copy(alpha = 0.5f), shape = CircleShape)
                        }
                    )
            )
        }
    }
}
