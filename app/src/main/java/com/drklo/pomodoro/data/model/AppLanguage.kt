package com.drklo.pomodoro.data.model

/** Supported UI languages (F-023). */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: RUSSIAN
    }
}
