package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Week journal (B1): 7 rows (today at top, going back), X axis = hours 0..24 with gridlines at
 * 0/4/8/12/16/20/24. Each completed pomodoro is a colored bar placed at its clock time.
 */
@Composable
fun WeekJournal(
    logs: List<PomodoroLog>,
    today: LocalDate,
    projectColors: Map<Long, Int>,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = Locale.getDefault()

    // Build 7 rows: index 0 = today, then previous days. Each row keeps its day label and key.
    val rows = (0..6).map { i ->
        val date = today.minusDays(i.toLong())
        val label = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth}"
        Triple(date.toString(), label, logs.filter { it.dayKey == date.toString() })
    }

    val rowCount = 7
    val gridHours = intArrayOf(0, 4, 8, 12, 16, 20, 24)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((rowCount * 30 + 28).dp)
    ) {
        val leftGutter = 42.dp.toPx()
        val bottomAxis = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotLeft = leftGutter
        val plotWidth = size.width - leftGutter
        val plotHeight = size.height - topPad - bottomAxis
        val rowH = plotHeight / rowCount

        val labelPaint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val hourPaint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Vertical hour gridlines + axis labels.
        for (h in gridHours) {
            val x = plotLeft + plotWidth * (h / 24f)
            drawLine(
                color = gridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotHeight),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                h.toString(), x, size.height - 4.dp.toPx(), hourPaint
            )
        }

        // Rows.
        rows.forEachIndexed { i, (_, label, dayLogs) ->
            val rowTop = topPad + i * rowH
            val barH = rowH * 0.55f
            val barTop = rowTop + (rowH - barH) / 2f

            drawContext.canvas.nativeCanvas.drawText(
                label, 4.dp.toPx(), rowTop + rowH / 2f + 4.dp.toPx(), labelPaint
            )

            dayLogs.forEach { log ->
                val startFrac = clockFraction(log.startEpochMs, zone)
                var endFrac = clockFraction(log.endEpochMs, zone)
                if (endFrac <= startFrac) endFrac = 24f // crossed midnight: clamp to end of day
                val x1 = plotLeft + plotWidth * (startFrac / 24f)
                val x2 = plotLeft + plotWidth * (endFrac / 24f)
                val w = (x2 - x1).coerceAtLeast(2.dp.toPx())
                val color = projectColors[log.projectId]?.let { Color(it) } ?: DefaultPomodoroColor
                drawRect(
                    color = color,
                    topLeft = Offset(x1, barTop),
                    size = Size(w, barH)
                )
            }
        }
    }
}

private fun clockFraction(epochMs: Long, zone: ZoneId): Float {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return t.hour + t.minute / 60f + t.second / 3600f
}
