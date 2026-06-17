package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.ProjectValue

/** Horizontal bars of per-project value within a window (C1). */
@Composable
fun ProjectBars(
    values: List<ProjectValue>,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        Text(
            stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val max = values.maxOf { it.value }.coerceAtLeast(1f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        values.forEach { v ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        v.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(valueFormatter(v.value), fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(v.value / max)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(v.colorArgb))
                    )
                }
            }
        }
    }
}
