package com.drklo.pomodoro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATE = booleanPreferencesKey("vibrate_enabled")
        val ALWAYS_ON = booleanPreferencesKey("always_on_display")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val AUTOSTART_BREAKS = booleanPreferencesKey("autostart_breaks")
        val IDLE_ALERT_MIN = intPreferencesKey("idle_alert_minutes")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        val DAY_END_MINUTE = intPreferencesKey("day_end_minute")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<GlobalSettings> = context.dataStore.data.map { p ->
        val defaults = GlobalSettings()
        GlobalSettings(
            soundEnabled = p[Keys.SOUND] ?: defaults.soundEnabled,
            vibrateEnabled = p[Keys.VIBRATE] ?: defaults.vibrateEnabled,
            alwaysOnDisplay = p[Keys.ALWAYS_ON] ?: defaults.alwaysOnDisplay,
            autostartPomodoros = p[Keys.AUTOSTART] ?: defaults.autostartPomodoros,
            autostartBreaks = p[Keys.AUTOSTART_BREAKS] ?: defaults.autostartBreaks,
            idleAlertMinutes = p[Keys.IDLE_ALERT_MIN] ?: defaults.idleAlertMinutes,
            dayEndHour = p[Keys.DAY_END_HOUR] ?: defaults.dayEndHour,
            dayEndMinute = p[Keys.DAY_END_MINUTE] ?: defaults.dayEndMinute,
            language = AppLanguage.fromTag(p[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromName(p[Keys.THEME])
        )
    }

    suspend fun setSoundEnabled(value: Boolean) = edit { it[Keys.SOUND] = value }
    suspend fun setVibrateEnabled(value: Boolean) = edit { it[Keys.VIBRATE] = value }
    suspend fun setAlwaysOnDisplay(value: Boolean) = edit { it[Keys.ALWAYS_ON] = value }
    suspend fun setAutostartPomodoros(value: Boolean) = edit { it[Keys.AUTOSTART] = value }
    suspend fun setAutostartBreaks(value: Boolean) = edit { it[Keys.AUTOSTART_BREAKS] = value }
    suspend fun setIdleAlertMinutes(value: Int) = edit { it[Keys.IDLE_ALERT_MIN] = value.coerceAtLeast(0) }

    suspend fun setDayEnd(hour: Int, minute: Int) = edit {
        it[Keys.DAY_END_HOUR] = hour.coerceIn(0, 23)
        it[Keys.DAY_END_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
