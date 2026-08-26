package com.drklo.pomodoro.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.drklo.pomodoro.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * A time of day, chosen through the platform clock dialog.
 *
 * The end of the logical day used to be a stepper over 0..1435 minutes in steps of five: 287
 * positions, and no way to say "eleven at night" other than travelling there. It is also not a
 * duration — it is a clock reading, and the system picker is both the familiar way to enter one and
 * the only thing that gets the 12- versus 24-hour question right on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayField(
    label: String,
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // The system's own 12/24-hour setting, not the locale's default: a user who has switched their
    // phone to 24-hour expects every clock on it to follow, this one included.
    val is24Hour = DateFormat.is24HourFormat(context)
    var picking by remember { mutableStateOf(false) }

    FieldRow(
        label = label,
        // Through Compose's locale rather than Locale.getDefault(): the app switches language
        // in-app, and a default read outside composition keeps rendering the old one.
        value = formatTimeOfDay(hour, minute, is24Hour, ComposeLocale.current.platformLocale),
        onClick = { picking = true },
        onClickLabel = stringResource(R.string.cd_edit_value),
        modifier = modifier
    )

    if (picking) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            // The dialog decides its own width instead of taking the platform default. That default
            // is a fraction of the screen's *shorter* side, so in landscape it came out around
            // 320dp — and Material lays the picker out side by side there, needing roughly 560dp.
            // The dial ran off the right edge of the dialog, taking half the minutes field with it.
            // Given the room, the horizontal layout is the one that belongs in landscape.
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH).padding(horizontal = 24.dp),
            // No title: the row that opened this already says what is being set, and in a landscape
            // window the height it would take is the difference between the dial fitting and not.
            text = {
                // A scroll as the last resort. Nothing should reach it on a phone, but a clipped
                // clock with no way to see the rest of it is the one outcome worth ruling out.
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(state.hour, state.minute)
                        picking = false
                    }
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Kept apart from the composable so it can be tested: 23:55 must read as "23:55" on a 24-hour phone
 * and as "11:55 PM" on a 12-hour one, in whichever language the app is currently running.
 */
internal fun formatTimeOfDay(hour: Int, minute: Int, is24Hour: Boolean, locale: Locale): String =
    if (is24Hour) {
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    } else {
        LocalTime.of(hour, minute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }

/** Wide enough for Material's side-by-side layout; beyond that the dialog would just be empty. */
private val DIALOG_MAX_WIDTH = 640.dp
