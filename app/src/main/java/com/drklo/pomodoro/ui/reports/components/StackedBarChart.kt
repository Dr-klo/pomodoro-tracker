package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.ui.reports.BarColumn

/**
 * Stacked bar chart: one bar per bucket, segmented by project color (B3 / C3).
 * Tapping a bar reports its index through [onSelect] (tapping the selected one clears it); the
 * selected bar keeps full color while the rest dim, so the tooltip below has an obvious anchor.
 */
@Composable
fun StackedBarChart(
    columns: List<BarColumn>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {}
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedLabelColor = MaterialTheme.colorScheme.onSurface
    val maxTotal = columns.maxOfOrNull { it.total }?.coerceAtLeast(1f) ?: 1f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(columns.size, selectedIndex) {
                detectTapGestures { offset ->
                    if (columns.isEmpty()) return@detectTapGestures
                    val index = (offset.x / (size.width / columns.size))
                        .toInt()
                        .coerceIn(0, columns.lastIndex)
                    onSelect(if (index == selectedIndex) null else index)
                }
            }
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
        val selectedPaint = android.graphics.Paint(labelPaint).apply {
            color = selectedLabelColor.toArgb()
            isFakeBoldText = true
        }

        columns.forEachIndexed { i, col ->
            val cx = slot * i + slot / 2f
            val left = cx - barWidth / 2f
            val isSelected = i == selectedIndex
            // Bars fade only once something else is selected.
            val alpha = if (selectedIndex == null || isSelected) 1f else 0.3f

            if (isSelected) {
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(slot * i + slot * 0.05f, 0f),
                    size = Size(slot * 0.9f, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }

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
                        size = Size(barWidth, h),
                        alpha = alpha
                    )
                    yBottom -= h
                }
            }

            drawContext.canvas.nativeCanvas.drawText(
                col.label, cx, size.height - 3.dp.toPx(), if (isSelected) selectedPaint else labelPaint
            )
        }
    }
}
