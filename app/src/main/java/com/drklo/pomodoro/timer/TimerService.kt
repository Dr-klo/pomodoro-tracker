package com.drklo.pomodoro.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.drklo.pomodoro.MainActivity
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows an ongoing notification while the timer runs or is paused
 * (PRD: reliable background timer via Foreground Service). Stops itself when the timer goes idle.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    private val engine: TimerEngine
        get() = (application as PomodoroApp).container.timerEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_RESET -> engine.reset()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (observeJob == null) {
            observeJob = scope.launch {
                engine.state.collectLatest { state ->
                    if (state.status == TimerStatus.IDLE) {
                        stopSelf()
                    } else {
                        notificationManager().notify(NOTIFICATION_ID, buildNotification())
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val state = engine.state.value
        val phaseText = when (state.phase) {
            Phase.POMODORO -> getString(R.string.phase_pomodoro)
            Phase.SHORT_BREAK -> getString(R.string.phase_short_break)
            Phase.LONG_BREAK -> getString(R.string.phase_long_break)
        }
        val title = state.project?.name?.let { "$it · $phaseText" } ?: phaseText
        val text = formatMmSs(state.remainingSeconds)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // Pause/Resume + Reset controls (F-101).
        if (state.status == TimerStatus.RUNNING) {
            builder.addAction(R.drawable.ic_notif_pause, getString(R.string.notif_pause), actionPendingIntent(ACTION_TOGGLE))
        } else {
            builder.addAction(R.drawable.ic_notif_play, getString(R.string.notif_resume), actionPendingIntent(ACTION_TOGGLE))
        }
        builder.addAction(R.drawable.ic_notif_reset, getString(R.string.notif_reset), actionPendingIntent(ACTION_RESET))

        return builder.build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.timer_channel_desc)
                setShowBadge(false)
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_TOGGLE = "com.drklo.pomodoro.action.TOGGLE"
        private const val ACTION_RESET = "com.drklo.pomodoro.action.RESET"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
