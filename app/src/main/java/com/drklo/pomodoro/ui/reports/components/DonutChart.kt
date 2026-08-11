package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.ProjectValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Donut chart of per-project distribution (C2), with a percentage legend below.
 * Tapping a slice (or its legend row) pulls that project out: the ring highlights it, the center
 * switches to its focus time and a tooltip spells out hours and pomodoros.
 */
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

    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = values.firstOrNull { it.projectId == selectedId }
    val selectedIndex = values.indexOfFirst { it.projectId == selectedId }.takeIf { it >= 0 }
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(170.dp)
                    .pointerInput(values, selectedId) {
                        detectTapGestures { offset ->
                            val index = sliceAt(offset, size.width.toFloat(), size.height.toFloat(), values, total)
                            selectedId = if (index == null || values[index].projectId == selectedId) {
                                null
                            } else {
                                values[index].projectId
                            }
                        }
                    }
            ) {
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
                values.forEachIndexed { i, v ->
                    val sweep = 360f * (v.value / total)
                    val isSelected = i == selectedIndex
                    // The selected slice keeps full color and grows; the others recede.
                    val extra = if (isSelected) stroke * 0.25f else 0f
                    drawArc(
                        color = Color(v.colorArgb),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke + extra),
                        alpha = if (selectedIndex == null || isSelected) 1f else 0.3f
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selected?.let { focusText(it.focusSec) } ?: centerLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = selected?.let { pomodorosText(it.pomodoros) }
                        ?: pomodorosText(values.sumOf { it.pomodoros }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            values.forEach { v ->
                val pct = (v.value / total * 100f).roundToInt()
                val isSelected = v.projectId == selectedId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedId = if (isSelected) null else v.projectId }
                        .background(if (isSelected) highlightColor else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
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

        ChartTooltip(
            title = selected?.name,
            focusSec = selected?.focusSec ?: 0,
            pomodoros = selected?.pomodoros ?: 0,
            hint = stringResource(R.string.chart_tap_hint_slice),
            note = selected?.let {
                stringResource(R.string.chart_share_of_total, (it.value / total * 100f).roundToInt())
            }
        )
    }
}

/** Index of the slice under [offset] in a [width]x[height] donut, or null if the tap missed it. */
private fun sliceAt(
    offset: Offset,
    width: Float,
    height: Float,
    values: List<ProjectValue>,
    total: Float
): Int? {
    val cx = width / 2f
    val cy = height / 2f
    val radius = minOf(width, height) / 2f
    if (hypot(offset.x - cx, offset.y - cy) > radius) return null
    // Degrees clockwise from 12 o'clock, where the first slice starts.
    val angle = (Math.toDegrees(atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble())) + 450.0) % 360.0
    var start = 0.0
    values.forEachIndexed { i, v ->
        val sweep = 360.0 * (v.value / total)
        if (angle >= start && angle < start + sweep) return i
        start += sweep
    }
    return values.lastIndex.takeIf { it >= 0 }
}
