package com.drklo.pomodoro.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.drklo.pomodoro.R

/**
 * Types a number straight into a [Stepper] field. The way in for someone who knows they want 47
 * minutes and should not have to travel there.
 */
@Composable
internal fun ValuePromptDialog(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }
    val entered = text.toIntOrNull()
    // Out of range is refused rather than silently clamped: someone who typed 300 into a 1..180
    // field meant something, and quietly saving 180 hides that it was not taken literally.
    val valid = entered != null && entered in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(MAX_DIGITS) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(stringResource(R.string.value_range, min, max))
            }
        },
        confirmButton = {
            TextButton(onClick = { entered?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Every range in the app is three digits or fewer; longer input is a typo in progress. */
private const val MAX_DIGITS = 4
