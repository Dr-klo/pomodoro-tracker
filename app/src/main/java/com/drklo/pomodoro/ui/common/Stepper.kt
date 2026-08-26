package com.drklo.pomodoro.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.R
import kotlin.math.roundToInt

/**
 * A labelled whole-number field: the value can be dragged to roughly the right place, nudged one
 * step at a time, or typed in outright.
 *
 * It used to be a bare pair of `-`/`+` buttons. Every duration in the app is set through this one
 * control, and with a step of one that meant 65 taps to go from the default 25-minute focus to 90 —
 * `IconButton` has no auto-repeat, so holding the finger down does nothing at all. The slider is
 * what removes the tapping; the buttons stay because a slider cannot reliably hit a single minute,
 * and typing stays for someone who already knows they want 47.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999,
    /** What one press of `-`/`+` changes. */
    step: Int = 1,
    /**
     * What the slider snaps to. Coarser than [step] for minutes (a five-minute grid covers 1..180
     * in a thumb's width) and equal to it for small counters, where every value is worth stopping on.
     */
    sliderStep: Int = step,
    valueText: (Int) -> String = { it.toString() }
) {
    var editing by remember { mutableStateOf(false) }
    val editLabel = stringResource(R.string.cd_edit_value)

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.weight(1f))
            // The value doubles as the way in to keyboard entry. onClickLabel is what says so: the
            // text alone reads as a number, not as something that can be activated.
            Text(
                text = valueText(value),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClickLabel = editLabel) { editing = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                enabled = value > min
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease))
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { raw -> onValueChange(snap(raw, min, max, sliderStep)) },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    // The slider is the whole field as far as a screen reader is concerned: it
                    // carries the name and reads the value out in the same words as the label above
                    // — "25 min", not "0.13".
                    .semantics {
                        contentDescription = label
                        stateDescription = valueText(value)
                    }
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceAtMost(max)) },
                enabled = value < max
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase))
            }
        }
    }

    if (editing) {
        ValuePromptDialog(
            label = label,
            value = value,
            min = min,
            max = max,
            onDismiss = { editing = false },
            onConfirm = {
                onValueChange(it)
                editing = false
            }
        )
    }
}

/**
 * Nearest multiple of [step] counted from [min], kept inside the range.
 *
 * The slider itself is continuous rather than using Compose's `steps`: that parameter needs the
 * range to divide evenly, and these ranges do not (0..120 by 5 does, 1..180 by 5 does not). Snapping
 * here handles both, and because the caller feeds the snapped number straight back as the slider's
 * value, the thumb still comes to rest on the grid.
 */
private fun snap(raw: Float, min: Int, max: Int, step: Int): Int {
    if (step <= 1) return raw.roundToInt().coerceIn(min, max)
    val stepsFromMin = ((raw - min) / step).roundToInt()
    return (min + stepsFromMin * step).coerceIn(min, max)
}

/** The label/value line without the controls, for fields that open a picker instead. */
@Composable
internal fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClickLabel = onClickLabel) { onClick() }
            .padding(vertical = 12.dp)
            // One node, not two: a screen reader should announce "day ends at, 23:55" and offer one
            // action, rather than walking a label and a number that look unrelated to each other.
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
