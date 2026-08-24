package com.drklo.pomodoro

import android.app.Application
import com.drklo.pomodoro.data.AppContainer
import com.drklo.pomodoro.data.model.AppLanguage
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

    /**
     * Language for [MainActivity.attachBaseContext], which runs before anything can suspend and
     * must not touch the disk: reading DataStore there blocked the main thread on every activity
     * creation and built a second repository behind [AppContainer]'s back. Primed once here and
     * kept current by the collector below.
     */
    @Volatile
    var languageTag: String = AppLanguage.RUSSIAN.tag
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
        // One read at process start, where the activity would otherwise pay for one per creation.
        languageTag = container.settingsRepository.currentLanguage().tag
        LocaleHelper.applyToProcess(languageTag)
        appScope.launch {
            container.settingsRepository.settings
                .map { it.language.tag }
                .distinctUntilChanged()
                .collect {
                    languageTag = it
                    LocaleHelper.applyToProcess(it)
                }
        }
    }

    private companion object {
        const val TAG = "PomodoroApp"
    }
}
