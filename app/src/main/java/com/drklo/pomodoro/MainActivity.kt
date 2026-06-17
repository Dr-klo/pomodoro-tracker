package com.drklo.pomodoro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.ThemeMode
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.ui.main.MainScreen
import com.drklo.pomodoro.ui.reports.ReportsScreen
import com.drklo.pomodoro.ui.settings.ProjectEditScreen
import com.drklo.pomodoro.ui.settings.SettingsScreen
import com.drklo.pomodoro.ui.theme.PomodoroTheme
import com.drklo.pomodoro.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun attachBaseContext(newBase: Context) {
        val tag = runBlocking { SettingsRepository(newBase).settings.first().language.tag }
        super.attachBaseContext(LocaleHelper.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            val container = (LocalContext.current.applicationContext as PomodoroApp).container
            val settings by container.settingsRepository.settings.collectAsState(initial = GlobalSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PomodoroTheme(darkTheme = darkTheme) {
                AppNavHost(modifier = Modifier)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val REPORTS = "reports"
    const val PROJECT_EDIT = "project/{projectId}"
    fun projectEdit(id: Long) = "project/$id"
}

@androidx.compose.runtime.Composable
private fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN, modifier = modifier) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenReports = { navController.navigate(Routes.REPORTS) }
            )
        }
        composable(Routes.REPORTS) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditProject = { id -> navController.navigate(Routes.projectEdit(id)) },
                onAddProject = { navController.navigate(Routes.projectEdit(-1L)) }
            )
        }
        composable(
            route = Routes.PROJECT_EDIT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: -1L
            ProjectEditScreen(projectId = id, onDone = { navController.popBackStack() })
        }
    }
}
