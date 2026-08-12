package com.drklo.pomodoro.util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A failed write should cost the user that one change, not the whole session.
 *
 * Without a handler, an exception in a root `launch` walks past `SupervisorJob` (which only keeps
 * siblings alive) and reaches the thread's default handler, which kills the process. A dropped
 * setting or an unsaved project is bad; losing the running pomodoro because of it is worse.
 */
fun loggingExceptionHandler(tag: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, error ->
        Log.e(tag, "Unhandled error in a background coroutine", error)
    }

/** [viewModelScope] launch that reports failures instead of taking the process down with them. */
fun ViewModel.launchSafely(block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(loggingExceptionHandler(this::class.java.simpleName), block = block)
