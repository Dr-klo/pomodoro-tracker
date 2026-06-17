package com.drklo.pomodoro.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase type label under the bullets. On stop shows the duration ("Pomodoro: 25 MIN"); while
 * running shows the countdown. Tapping it changes the phase type (F-021), only when enabled.
 */
@Composable
fun PhaseLabel(
    text: String,
    color: Color,
    changeEnabled: Boolean,
    onChangePhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .then(if (changeEnabled) Modifier.clickable { onChangePhase() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
