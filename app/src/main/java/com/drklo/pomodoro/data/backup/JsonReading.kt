package com.drklo.pomodoro.data.backup

import android.util.JsonReader
import android.util.JsonToken

/**
 * Reading primitives for [ProjectBackup].
 *
 * They live beside it rather than inside it because they are about JSON, not about backups: each
 * one turns one of JsonReader's low-level complaints — a wrong token, a number that is not one —
 * into a named refusal the user can be shown. Keeping them here leaves ProjectBackup itself to say
 * what a backup is.
 */

internal inline fun JsonReader.readArray(read: () -> Unit) {
    expect(JsonToken.BEGIN_ARRAY) { beginArray() }
    while (hasNext()) read()
    endArray()
}

internal inline fun JsonReader.expect(token: JsonToken, enter: () -> Unit) {
    if (peek() != token) {
        throw ProjectBackup.BackupException(ProjectBackup.Failure.MALFORMED, "expected $token but found ${peek()}")
    }
    enter()
}

internal fun JsonReader.nextInt(field: String): Int = try {
    nextInt()
} catch (e: NumberFormatException) {
    throw ProjectBackup.BackupException(
        ProjectBackup.Failure.INVALID_VALUE,
        "\"$field\" is not a whole number"
    ).initCause(e) as ProjectBackup.BackupException
}

internal fun JsonReader.nextLong(field: String): Long = try {
    nextLong()
} catch (e: NumberFormatException) {
    throw ProjectBackup.BackupException(
        ProjectBackup.Failure.INVALID_VALUE,
        "\"$field\" is not a number"
    ).initCause(e) as ProjectBackup.BackupException
}

internal fun JsonReader.nextString(field: String): String {
    if (peek() != JsonToken.STRING) {
        throw ProjectBackup.BackupException(ProjectBackup.Failure.MALFORMED, "\"$field\" is not text")
    }
    return nextString()
}

internal fun JsonReader.nextBooleanOrFail(field: String): Boolean {
    if (peek() != JsonToken.BOOLEAN) {
        throw ProjectBackup.BackupException(ProjectBackup.Failure.MALFORMED, "\"$field\" is not true or false")
    }
    return nextBoolean()
}
