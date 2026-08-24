package com.drklo.pomodoro.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ThemeMode
import com.drklo.pomodoro.ui.ViewModelFactories
import com.drklo.pomodoro.ui.common.ConfirmDeleteProjectDialog
import com.drklo.pomodoro.ui.common.SegmentedChoice
import com.drklo.pomodoro.ui.common.Stepper
import com.drklo.pomodoro.util.BatteryOptimization
import com.drklo.pomodoro.util.findActivity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProject: (Long) -> Unit,
    onAddProject: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactories.settings)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The project the user asked to delete, held until they confirm.
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    pendingDelete?.let { project ->
        ConfirmDeleteProjectDialog(
            projectName = project.name,
            onConfirm = {
                pendingDelete = null
                viewModel.deleteProject(project)
            },
            onDismiss = { pendingDelete = null }
        )
    }

    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = BatteryOptimization.isIgnoring(context) }
    // Also re-checked whenever the screen comes back: the setting can be changed from Android's own
    // settings, and the launcher callback only ever fires for the trip this screen started.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryExempt = BatteryOptimization.isIgnoring(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Projects ---
            item {
                SettingsGroup(stringResource(R.string.section_projects)) {
                    projects.forEachIndexed { index, project ->
                        if (index > 0) RowDivider()
                        ProjectRow(
                            project = project,
                            onClick = { onEditProject(project.id) },
                            // The last project may not be deleted: an empty carousel leaves the
                            // main screen with nothing to act on.
                            canDelete = projects.size > 1,
                            onDelete = { pendingDelete = project }
                        )
                    }
                    RowDivider()
                    TextButton(
                        onClick = onAddProject,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.action_add_project),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // --- General ---
            item {
                SettingsGroup(stringResource(R.string.section_general)) {
                    SwitchRow(stringResource(R.string.setting_sound), settings.soundEnabled, viewModel::setSound)
                    SwitchRow(stringResource(R.string.setting_vibrate), settings.vibrateEnabled, viewModel::setVibrate)
                    SwitchRow(stringResource(R.string.setting_always_on), settings.alwaysOnDisplay, viewModel::setAlwaysOn)
                    SwitchRow(stringResource(R.string.setting_autostart_pomodoros), settings.autostartPomodoros, viewModel::setAutostartPomodoros)
                    SwitchRow(stringResource(R.string.setting_autostart_breaks), settings.autostartBreaks, viewModel::setAutostartBreaks)
                    SwitchRow(stringResource(R.string.setting_hold_finished_color), settings.holdFinishedPhaseColor, viewModel::setHoldFinishedPhaseColor)
                    Caption(stringResource(R.string.setting_hold_finished_color_summary))
                    RowDivider()
                    Stepper(
                        label = stringResource(R.string.setting_idle_alert),
                        value = settings.idleAlertMinutes,
                        onValueChange = viewModel::setIdleAlertMinutes,
                        min = 0, max = 120,
                        valueText = { v ->
                            if (v == 0) context.getString(R.string.value_off)
                            else "$v ${context.getString(R.string.minutes_unit)}"
                        }
                    )
                    Caption(stringResource(R.string.setting_idle_alert_summary))
                    Stepper(
                        label = stringResource(R.string.setting_day_end),
                        value = settings.dayEndHour * 60 + settings.dayEndMinute,
                        onValueChange = { total -> viewModel.setDayEnd(total / 60, total % 60) },
                        min = 0, max = 23 * 60 + 55, step = 5,
                        valueText = { total -> String.format(Locale.US, "%02d:%02d", total / 60, total % 60) }
                    )
                    Caption(stringResource(R.string.setting_day_end_summary))
                }
            }

            // --- Background reliability ---
            item {
                SettingsGroup(stringResource(R.string.section_background)) {
                    if (batteryExempt) {
                        Text(
                            stringResource(R.string.battery_ok),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching { batteryLauncher.launch(BatteryOptimization.requestIntent(context)) }
                                        .onFailure { batteryLauncher.launch(BatteryOptimization.settingsListIntent()) }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.battery_action))
                            Caption(stringResource(R.string.battery_summary))
                        }
                    }
                }
            }

            // --- Appearance ---
            item {
                SettingsGroup(stringResource(R.string.section_appearance)) {
                    SegmentedChoice(
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark)
                        ),
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // --- Language ---
            item {
                SettingsGroup(stringResource(R.string.setting_language)) {
                    SegmentedChoice(
                        options = listOf(
                            AppLanguage.ENGLISH to "English",
                            AppLanguage.RUSSIAN to "Русский"
                        ),
                        selected = settings.language,
                        onSelect = { lang ->
                            if (settings.language != lang) {
                                viewModel.setLanguage(lang) { context.findActivity()?.recreate() }
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(project.pomodoroColor))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(project.name, fontWeight = FontWeight.Medium)
            Text(
                "${project.focusMinutes}/${project.shortBreakMinutes} · ${project.pomodorosPerSession}🍅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
