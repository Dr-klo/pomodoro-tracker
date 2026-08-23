package com.drklo.pomodoro

import android.app.Application
import com.drklo.pomodoro.data.AppContainer
import com.drklo.pomodoro.util.LocaleHelper
import com.drklo.pomodoro.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PomodoroApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + loggingExceptionHandler(TAG))

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.projectRepository.ensureSeeded()
        }
        // Whoever started the process — the launcher or a resurrected service — the reports must
        // format dates in the language the user picked, so the JVM default is set here and follows
        // the setting from then on.
        appScope.launch {
            container.settingsRepository.settings
                .map { it.language.tag }
                .distinctUntilChanged()
                .collect { LocaleHelper.applyToProcess(it) }
        }
    }

    private companion object {
        const val TAG = "PomodoroApp"
    }
}
