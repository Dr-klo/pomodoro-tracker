package com.drklo.pomodoro.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

/**
 * Sound and vibration feedback for phase boundaries (global settings F-010, F-016).
 * Plays crisp "ding" tones synthesized at runtime by [ToneSynth] (no bundled audio assets).
 */
class TimerEffects(context: Context) : PhaseFeedback {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var startSoundId: Int = 0
    private var endSoundId: Int = 0

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        runCatching {
            val dir = appContext.cacheDir
            val startFile = File(dir, "tone_start.wav")
            val endFile = File(dir, "tone_end.wav")
            // Short higher click for start.
            ToneSynth.writeBell(
                startFile, freqs = doubleArrayOf(1568.0), weights = doubleArrayOf(1.0),
                durationSec = 0.16, decay = 22.0
            )
            // Clear two-harmonic "ding" for the end of an interval.
            ToneSynth.writeBell(
                endFile, freqs = doubleArrayOf(1318.5, 2637.0), weights = doubleArrayOf(1.0, 0.45),
                durationSec = 0.6, decay = 6.5
            )
            startSoundId = soundPool.load(startFile.absolutePath, 1)
            endSoundId = soundPool.load(endFile.absolutePath, 1)
        }
    }

    override fun playStart() {
        if (startSoundId != 0) soundPool.play(startSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun playEnd() {
        if (endSoundId != 0) soundPool.play(endSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun vibrate() = vibrate(DEFAULT_VIBRATION_MS)

    fun vibrate(durationMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val DEFAULT_VIBRATION_MS = 400L
    }
}
