package com.drklo.pomodoro.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Applies the UI language chosen in the app (F-023), which is deliberately independent of the
 * system language.
 *
 * Two things are needed and they are kept apart. [wrap] hands back a context whose resources speak
 * the chosen language — that covers everything loaded through `getString`. [applyToProcess] sets the
 * JVM default, which is what `Locale.getDefault()` returns: month names, weekday names and the
 * first day of the week in the reports all come from there. Merging them, as `wrap` used to, hid a
 * process-wide side effect behind a name that promises a value.
 */
object LocaleHelper {

    /**
     * The chosen language over the device's own region.
     *
     * A bare language tag is not enough. Calendar conventions come from the region, not the
     * language: `Locale("ru")` has no region at all, and the JVM answers "the week starts on
     * Sunday" for it — so a Russian user would get an American week. The region is read from the
     * system configuration, which [Locale.setDefault] cannot disturb, so "Russian text, Monday
     * weeks" and "English text, Monday weeks" both come out right for a phone set to Russia.
     */
    fun localeFor(languageTag: String): Locale =
        localeFor(languageTag, Resources.getSystem().configuration.locales[0].country)

    /**
     * The pure half of [localeFor], so the rule itself can be tested without a device.
     *
     * Built through [Locale.Builder] rather than the `Locale(language, country)` constructor, which
     * Java deprecated. `Locale.of` would be the direct replacement but it arrived in Java 19, well
     * above this app's `minSdk`.
     */
    fun localeFor(languageTag: String, systemRegion: String): Locale =
        Locale.Builder().setLanguage(languageTag).setRegion(systemRegion).build()

    /** A context that resolves resources in [languageTag]. Pure: nothing outside it changes. */
    fun wrap(context: Context, languageTag: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(localeFor(languageTag))
        return context.createConfigurationContext(config)
    }

    /**
     * Points `Locale.getDefault()` at the chosen language. Called from `Application.onCreate`, so it
     * holds however the process was started — a service resurrected by START_STICKY has no activity
     * to run `attachBaseContext`, and without this the same date would format differently depending
     * on who woke the process up.
     */
    fun applyToProcess(languageTag: String) {
        Locale.setDefault(localeFor(languageTag))
    }
}
