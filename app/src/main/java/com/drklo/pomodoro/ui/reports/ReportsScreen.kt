package com.drklo.pomodoro.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.components.ChartCard
import com.drklo.pomodoro.ui.reports.components.WeekJournal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val projectColors by viewModel.projectColors.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_reports)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_tomatoes)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_projects)) })
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val hLabel = stringResource(R.string.unit_hours)
                val mLabel = stringResource(R.string.unit_minutes)
                when (tab) {
                    0 -> {
                        SummaryRow(
                            stringResource(R.string.stat_focus_time),
                            stat(stringResource(R.string.stat_total), formatFocus(summary.totalFocusSec, hLabel, mLabel)),
                            stat(stringResource(R.string.stat_today), formatFocus(summary.todayFocusSec, hLabel, mLabel)),
                            stat(stringResource(R.string.stat_week), formatFocus(summary.weekFocusSec, hLabel, mLabel))
                        )
                        ChartCard(
                            title = stringResource(R.string.chart_week_journal),
                            subtitle = stringResource(
                                R.string.chart_week_journal_sub,
                                formatFocus(summary.weekFocusSec, hLabel, mLabel)
                            )
                        ) {
                            WeekJournal(logs = logs, today = today, projectColors = projectColors)
                        }
                    }
                    else -> {
                        SummaryRow(
                            stringResource(R.string.stat_pomodoros),
                            stat(stringResource(R.string.stat_total), summary.totalPomodoros.toString()),
                            stat(stringResource(R.string.stat_today), summary.todayPomodoros.toString()),
                            stat(stringResource(R.string.stat_week), summary.weekPomodoros.toString())
                        )
                    }
                }
            }
        }
    }
}

private data class Stat(val label: String, val value: String)

private fun stat(label: String, value: String) = Stat(label, value)

@Composable
private fun SummaryRow(title: String, vararg stats: Stat) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
