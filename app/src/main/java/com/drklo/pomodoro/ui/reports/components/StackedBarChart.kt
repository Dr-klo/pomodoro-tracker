package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.ui.reports.BarColumn

/** Stacked bar chart: one bar per bucket, segmented by project color (B3 / C3). */
@Composable
fun StackedBarChart(
    columns: List<BarColumn>,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val maxTotal = columns.maxOfOrNull { it.total }?.coerceAtLeast(1f) ?: 1f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val bottomAxis = 16.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotHeight = size.height - bottomAxis - topPad
        val n = columns.size.coerceAtLeast(1)
        val slot = size.width / n
        val barWidth = slot * 0.6f

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        columns.forEachIndexed { i, col ->
            val cx = slot * i + slot / 2f
            val left = cx - barWidth / 2f

            if (col.total <= 0f) {
                // baseline tick for empty buckets
                drawLine(
                    color = emptyColor,
                    start = Offset(left, topPad + plotHeight),
                    end = Offset(left + barWidth, topPad + plotHeight),
                    strokeWidth = 2f
                )
            } else {
                var yBottom = topPad + plotHeight
                col.segments.forEach { seg ->
                    val h = plotHeight * (seg.value / maxTotal)
                    drawRect(
                        color = Color(seg.colorArgb),
                        topLeft = Offset(left, yBottom - h),
                        size = Size(barWidth, h)
                    )
                    yBottom -= h
                }
            }

            drawContext.canvas.nativeCanvas.drawText(
                col.label, cx, size.height - 3.dp.toPx(), labelPaint
            )
        }
    }
}
