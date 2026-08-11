package com.drklo.pomodoro.ui.reports.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.formatFocus

/** "1h 20m" for the given focus seconds, using the localized unit labels. */
@Composable
fun focusText(focusSec: Int): String = formatFocus(
    focusSec,
    stringResource(R.string.unit_hours),
    stringResource(R.string.unit_minutes)
)

/** "4 pomodoros", correctly pluralized. */
@Composable
fun pomodorosText(count: Int): String =
    pluralStringResource(R.plurals.pomodoros_count, count, count)

/** The headline of every tooltip: "1h 20m · 4 pomodoros". */
@Composable
fun metricsText(focusSec: Int, pomodoros: Int): String =
    stringResource(R.string.chart_metrics, focusText(focusSec), pomodorosText(pomodoros))

/** One line of a tooltip's per-project breakdown. */
data class TooltipRow(val name: String, val colorArgb: Int, val focusSec: Int, val pomodoros: Int)

/**
 * The detail panel a chart shows for the tapped element: what was tapped, how much time it holds
 * and how many pomodoros, plus an optional per-project breakdown. Renders [hint] in the same slot
 * while nothing is selected, so the chart advertises that it is tappable and keeps a stable height.
 */
@Composable
fun ChartTooltip(
    title: String?,
    focusSec: Int,
    pomodoros: Int,
    hint: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    rows: List<TooltipRow> = emptyList()
) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 10.dp)) {
        AnimatedVisibility(visible = title == null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        AnimatedVisibility(
            visible = title != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = metricsText(focusSec, pomodoros),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (note != null) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rows.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rows.forEach { row ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(row.colorArgb))
                                    )
                                    Text(
                                        text = row.name,
                                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = metricsText(row.focusSec, row.pomodoros),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
