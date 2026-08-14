package com.drklo.pomodoro.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drklo.pomodoro.R

/**
 * Guard in front of both delete paths (the row in settings and the editor's toolbar), which sit one
 * finger-width from targets that only open a project. Deleting is soft, so the wording says what
 * actually happens: the project leaves the carousel, the pomodoros stay in the reports.
 */
@Composable
fun ConfirmDeleteProjectDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_project_title)) },
        text = { Text(stringResource(R.string.dialog_delete_project_text, projectName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
