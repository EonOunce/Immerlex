package com.immerlex.app.ui

/** A snackbar/toast request: resource id plus format args, resolved by the UI layer. */
data class UiMessage(val resId: Int, val args: List<Any> = emptyList())
