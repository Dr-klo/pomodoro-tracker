package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.ProjectValue
import kotlin.math.roundToInt

/** Donut chart of per-project distribution (C2), with a percentage legend below. */
@Composable
fun DonutChart(
    values: List<ProjectValue>,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val total = values.sumOf { it.value.toDouble() }.toFloat()
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    if (total <= 0f) {
        Text(
            stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(170.dp)) {
                val stroke = size.minDimension * 0.18f
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                var start = -90f
                values.forEach { v ->
                    val sweep = 360f * (v.value / total)
                    drawArc(
                        color = Color(v.colorArgb),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Text(centerLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            values.forEach { v ->
                val pct = (v.value / total * 100f).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(v.colorArgb))
                    )
                    Text(
                        v.name,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("$pct%", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
