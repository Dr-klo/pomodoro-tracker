package com.drklo.pomodoro.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.common.Stepper
import com.drklo.pomodoro.util.BatteryOptimization
import com.drklo.pomodoro.util.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProject: (Long) -> Unit,
    onAddProject: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var batteryExempt by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(BatteryOptimization.isIgnoring(context))
    }
    val batteryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = BatteryOptimization.isIgnoring(context) }

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // --- Projects ---
            item { SectionHeader(stringResource(R.string.section_projects)) }
            items(projects, key = { it.id }) { project ->
                ProjectRow(
                    project = project,
                    onClick = { onEditProject(project.id) },
                    onDelete = { viewModel.deleteProject(project) }
                )
            }
            item {
                TextButton(onClick = onAddProject, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.action_add_project), modifier = Modifier.padding(start = 8.dp))
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }

            // --- General ---
            item { SectionHeader(stringResource(R.string.section_general)) }
            item {
                SwitchRow(stringResource(R.string.setting_sound), settings.soundEnabled, viewModel::setSound)
            }
            item {
                SwitchRow(stringResource(R.string.setting_vibrate), settings.vibrateEnabled, viewModel::setVibrate)
            }
            item {
                SwitchRow(stringResource(R.string.setting_always_on), settings.alwaysOnDisplay, viewModel::setAlwaysOn)
            }
            item {
                SwitchRow(stringResource(R.string.setting_autostart_pomodoros), settings.autostartPomodoros, viewModel::setAutostartPomodoros)
            }
            item {
                SwitchRow(stringResource(R.string.setting_autostart_breaks), settings.autostartBreaks, viewModel::setAutostartBreaks)
            }
            item {
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
            }
            item {
                Text(
                    stringResource(R.string.setting_idle_alert_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Stepper(
                    label = stringResource(R.string.setting_day_end),
                    modifier = Modifier.padding(top = 12.dp),
                    value = settings.dayEndHour * 60 + settings.dayEndMinute,
                    onValueChange = { total -> viewModel.setDayEnd(total / 60, total % 60) },
                    min = 0, max = 23 * 60 + 55, step = 5,
                    valueText = { total ->
                        String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
                    }
                )
            }
            item {
                Text(
                    stringResource(R.string.setting_day_end_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }

            // --- Background reliability (battery optimization, Samsung) ---
            item { SectionHeader(stringResource(R.string.section_background)) }
            item {
                if (batteryExempt) {
                    Text(
                        stringResource(R.string.battery_ok),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching { batteryLauncher.launch(BatteryOptimization.requestIntent(context)) }
                                    .onFailure { batteryLauncher.launch(BatteryOptimization.settingsListIntent()) }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.battery_action))
                        Text(
                            stringResource(R.string.battery_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }

            // --- Language ---
            item { SectionHeader(stringResource(R.string.setting_language)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = {
                                if (settings.language != lang) {
                                    viewModel.setLanguage(lang)
                                    context.findActivity()?.recreate()
                                }
                            },
                            label = { Text(lang.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(project.pomodoroColor))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(project.name, fontWeight = FontWeight.Medium)
            Text(
                "${project.focusMinutes}/${project.shortBreakMinutes} · ${project.pomodorosPerSession}🍅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
        }
    }
}
