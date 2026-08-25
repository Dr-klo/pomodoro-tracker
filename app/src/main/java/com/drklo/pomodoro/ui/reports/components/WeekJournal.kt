package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.dayTitle
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * Week journal (B1): 7 rows (today at top, going back), X axis = hours 0..24 with gridlines at
 * 0/4/8/12/16/20/24. Each completed pomodoro is a colored bar placed at its clock time.
 * Tapping a row selects that day and reports its focus time, pomodoro count and project split.
 */
@Composable
fun WeekJournal(
    logs: List<PomodoroLog>,
    today: LocalDate,
    projects: List<Project>,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant
    val locale = LocalLocale.current.platformLocale
    val projectColors = remember(projects) { projects.associate { it.id to it.pomodoroColor } }

    // Build 7 rows: index 0 = today, then previous days. Each row keeps its day label and logs.
    val rows = (0..6).map { i ->
        val date = today.minusDays(i.toLong())
        val label = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth}"
        Triple(date, label, logs.filter { it.dayKey == date.toString() })
    }

    val gridHours = intArrayOf(0, 4, 8, 12, 16, 20, 24)
    var selectedRow by remember { mutableStateOf<Int?>(null) }
    val currentSelection by rememberUpdatedState(selectedRow)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CANVAS_HEIGHT)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val index = rowAt(offset.y, size.height.toFloat()) ?: return@detectTapGestures
                        selectedRow = if (index == currentSelection) null else index
                    }
                }
        ) {
            val plotLeft = LEFT_GUTTER.toPx()
            val plotWidth = size.width - plotLeft
            val topPad = TOP_PAD.toPx()
            val plotHeight = plotHeight(size.height)
            val rowH = rowHeight(size.height)

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

            // Selected row band, drawn under everything else.
            selectedRow?.let { i ->
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(0f, topPad + i * rowH),
                    size = Size(size.width, rowH),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
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
                val alpha = if (selectedRow == null || selectedRow == i) 1f else 0.35f

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
                        size = Size(w, barH),
                        alpha = alpha
                    )
                }
            }
        }

        val selection = selectedRow?.let { rows.getOrNull(it) }
        val dayLogs = selection?.third.orEmpty()
        ChartTooltip(
            title = selection?.first?.format(dayTitle.withLocale(locale)),
            focusSec = dayLogs.sumOf { it.durationSeconds },
            pomodoros = dayLogs.size,
            hint = stringResource(R.string.chart_tap_hint_day),
            rows = projects.mapNotNull { p ->
                val forProject = dayLogs.filter { it.projectId == p.id }
                if (forProject.isEmpty()) null
                else TooltipRow(
                    name = p.name,
                    colorArgb = p.pomodoroColor,
                    focusSec = forProject.sumOf { it.durationSeconds },
                    pomodoros = forProject.size
                )
            }
        )
    }
}

// Grid geometry, defined once: the draw pass and the hit test must agree on where a row is, so
// neither is allowed its own copy of these numbers.
private const val ROW_COUNT = 7
private val ROW_SPACING = 30.dp
private val TOP_PAD = 6.dp
private val BOTTOM_AXIS = 18.dp
private val LEFT_GUTTER = 42.dp
private val CANVAS_HEIGHT = ROW_SPACING * ROW_COUNT + TOP_PAD + BOTTOM_AXIS + 4.dp

private fun Density.plotHeight(canvasHeight: Float): Float =
    canvasHeight - TOP_PAD.toPx() - BOTTOM_AXIS.toPx()

private fun Density.rowHeight(canvasHeight: Float): Float = plotHeight(canvasHeight) / ROW_COUNT

/** Row index under [y] on a canvas of [canvasHeight], or null if the tap fell outside the grid. */
private fun Density.rowAt(y: Float, canvasHeight: Float): Int? =
    ((y - TOP_PAD.toPx()) / rowHeight(canvasHeight)).toInt().takeIf { it in 0 until ROW_COUNT }

private fun clockFraction(epochMs: Long, zone: ZoneId): Float {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return t.hour + t.minute / 60f + t.second / 3600f
}
