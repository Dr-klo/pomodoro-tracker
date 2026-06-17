package com.drklo.pomodoro.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Walks the ContextWrapper chain to find the host Activity, if any. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
