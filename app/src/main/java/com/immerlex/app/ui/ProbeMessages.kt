package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.ai.ProbeFailure

/** One wording table per gateway failure, shared by the settings probe and chapter generation. */
fun probeFailureLabel(failure: ProbeFailure): Int = when (failure) {
    ProbeFailure.MISSING_KEY -> R.string.settings_probe_missing_key
    ProbeFailure.UNAUTHORIZED -> R.string.settings_probe_unauthorized
    ProbeFailure.NOT_FOUND -> R.string.settings_probe_not_found
    ProbeFailure.TLS -> R.string.settings_probe_tls
    ProbeFailure.TIMEOUT -> R.string.settings_probe_timeout
    ProbeFailure.UNREACHABLE -> R.string.settings_probe_unreachable
    ProbeFailure.NONE, ProbeFailure.HTTP_ERROR -> R.string.settings_probe_http_error
}

/** Gateway text a user cannot act on, so keep only a readable head of it after the advice. */
fun probeDetailOf(detail: String): String = detail.take(MAX_PROBE_DETAIL).ifBlank { " " }

private const val MAX_PROBE_DETAIL = 160
