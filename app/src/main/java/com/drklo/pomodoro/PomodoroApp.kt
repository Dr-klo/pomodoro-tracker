package com.drklo.pomodoro

import android.app.Application
import com.drklo.pomodoro.data.AppContainer
import com.drklo.pomodoro.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }

    private companion object {
        const val TAG = "PomodoroApp"
    }
}
