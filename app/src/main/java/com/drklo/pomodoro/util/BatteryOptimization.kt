package com.drklo.pomodoro.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Helpers for the battery-optimization exemption. Samsung One UI aggressively puts apps to
 * "deep sleep", which can kill a long-running foreground timer; exempting the app keeps it alive.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** System dialog asking the user to exempt this app from battery optimization. */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri()
        )

    /** Fallback: the full battery-optimization list, if the direct request can't be shown. */
    fun settingsListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
