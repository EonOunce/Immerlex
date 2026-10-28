package com.immerlex.app.ui

import android.content.Context

/** A snackbar/toast request: resource id plus format args, resolved by the UI layer. */
data class UiMessage(val resId: Int, val args: List<Any> = emptyList())

/**
 * A mis-typed format arg once crashed the process from a snackbar (%1$d fed a String),
 * so fall back to the unformatted text: a cosmetic loss beats taking the app down.
 * An argument may itself be a [UiMessage], which is how wording stays in strings.xml even
 * when a message is assembled from a list of reasons.
 */
fun Context.textFor(message: UiMessage): String = runCatching {
    val args = message.args.map { if (it is UiMessage) textFor(it) else it }
    getString(message.resId, *args.toTypedArray())
}.getOrElse { getString(message.resId) }
