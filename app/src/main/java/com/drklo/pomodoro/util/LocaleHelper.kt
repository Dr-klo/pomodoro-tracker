package com.drklo.pomodoro.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies a UI language by wrapping a context with an overridden locale (F-023). */
object LocaleHelper {

    fun wrap(context: Context, languageTag: String): Context {
        val locale = Locale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
