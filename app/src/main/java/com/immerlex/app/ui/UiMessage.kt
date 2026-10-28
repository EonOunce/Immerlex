package com.immerlex.app.ui

import android.content.Context

/** A snackbar/toast request: resource id plus format args, resolved by the UI layer. */
data class UiMessage(val resId: Int, val args: List<Any> = emptyList())

/**
 * A mis-typed format arg once crashed the process from a snackbar (%1$d fed a String),
 * so fall back to the unformatted text: a cosmetic loss beats taking the app down.
 */
fun Context.textFor(message: UiMessage): String = runCatching {
    getString(message.resId, *message.args.toTypedArray())
}.getOrElse { getString(message.resId) }
