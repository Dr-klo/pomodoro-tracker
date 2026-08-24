package com.drklo.pomodoro.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.drklo.pomodoro.MainActivity
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows an ongoing notification while the timer runs or is paused
 * (PRD: reliable background timer via Foreground Service).
 *
 * **Who owns the lifecycle.** One rule, in one direction: the service is *started* when the user
 * starts a phase from the UI, and it *stops itself* the moment the timer reports IDLE. Nothing else
 * calls [stopService] — an owner split between the ViewModel and the service is how a phase ends up
 * running with no notification behind it. Autostarted phases need no start of their own: the engine
 * hands over from one phase to the next without ever publishing an IDLE frame in between, so the
 * service that was already running simply keeps going.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    /** Last posted appearance; see [notificationSignature]. */
    private var lastSignature: String? = null

    private val engine: TimerEngine
        get() = (application as PomodoroApp).container.timerEngine

    /**
     * Strings for the notification. The service's own resources follow the *system* language, so
     * without this the one element visible on a locked screen would ignore the language chosen in
     * the app (F-023) — a Russian phone with the app set to English kept announcing «Помодоро».
     */
    private lateinit var localized: Context

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val tag = (application as PomodoroApp).languageTag
        localized = LocaleHelper.wrap(this, tag)
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
                // The delay after each post is the rate limit: a StateFlow conflates by nature, so
                // whatever the timer does while we wait collapses into a single latest value.
                // Scrubbing the dial emits on every touch move, and one notify() per move floods
                // NotificationManager until Android starts dropping updates ("has posted too many
                // notifications") — paying IPC and a shade redraw for each one on the way there.
                engine.state.collect { state ->
                    if (state.status == TimerStatus.IDLE) {
                        stopSelf()
                        return@collect
                    }
                    // The countdown itself is drawn by the system chronometer, so a re-post is only
                    // worth it when something else moved: the phase, the run state, or the progress
                    // bar's next step. Re-posting every second is what made the shade flicker.
                    val signature = state.notificationSignature()
                    if (signature != lastSignature) {
                        lastSignature = signature
                        notificationManager().notify(NOTIFICATION_ID, buildNotification())
                    }
                    delay(MIN_NOTIFICATION_INTERVAL_MS)
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
            Phase.POMODORO -> localized.getString(R.string.phase_pomodoro)
            Phase.SHORT_BREAK -> localized.getString(R.string.phase_short_break)
            Phase.LONG_BREAK -> localized.getString(R.string.phase_long_break)
        }
        val title = state.project?.name?.let { "$it · $phaseText" } ?: phaseText
        val elapsed = (state.totalSeconds - state.remainingSeconds).coerceAtLeast(0)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // How far through the phase, without having to read the digits.
            .setProgress(state.totalSeconds.coerceAtLeast(1), elapsed, false)
            // Public: the timer is the reason the phone is on the desk, so it belongs on the lock
            // screen. There is nothing private in "Работа · Помодоро".
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.status == TimerStatus.RUNNING) {
            // Hand the second-by-second countdown to the system: it ticks the chronometer itself,
            // so the app does not have to re-post the notification once a second to keep it honest.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + state.remainingSeconds * MILLIS_PER_SECOND)
                .setShowWhen(true)
        } else {
            // Parked: a chronometer would keep counting, so the frozen time is plain text.
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(formatMmSs(state.remainingSeconds))
        }

        // Pause/Resume + Reset controls (F-101).
        if (state.status == TimerStatus.RUNNING) {
            builder.addAction(
                R.drawable.ic_notif_pause,
                localized.getString(R.string.notif_pause),
                actionPendingIntent(ACTION_TOGGLE)
            )
        } else {
            builder.addAction(
                R.drawable.ic_notif_play,
                localized.getString(R.string.notif_resume),
                actionPendingIntent(ACTION_TOGGLE)
            )
        }
        builder.addAction(
            R.drawable.ic_notif_reset,
            localized.getString(R.string.notif_reset),
            actionPendingIntent(ACTION_RESET)
        )

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
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localized.getString(R.string.timer_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * What the notification would look like. Everything the user can see except the ticking
     * seconds, which the system draws on its own — so an unchanged signature means a re-post would
     * redraw the shade for nothing.
     */
    private fun TimerState.notificationSignature(): String {
        val step = if (totalSeconds > 0) {
            (totalSeconds - remainingSeconds) * PROGRESS_STEPS / totalSeconds
        } else {
            0
        }
        return "$status|$phase|${project?.id}|$step"
    }

    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1001

        /** The countdown only ever shows whole seconds, so posting faster than this buys nothing. */
        private const val MIN_NOTIFICATION_INTERVAL_MS = 250L

        private const val MILLIS_PER_SECOND = 1000L

        /** Progress-bar resolution; finer steps would only mean more re-posts nobody can see. */
        private const val PROGRESS_STEPS = 100
        private const val ACTION_TOGGLE = "com.drklo.pomodoro.action.TOGGLE"
        private const val ACTION_RESET = "com.drklo.pomodoro.action.RESET"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }
    }
}
