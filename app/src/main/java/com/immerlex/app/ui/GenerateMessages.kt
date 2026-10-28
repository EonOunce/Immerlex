package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.ai.GenerateOutcome
import com.immerlex.core.db.KIND_SHORT

fun GenerateOutcome.toUiMessage(): UiMessage = when (this) {
    is GenerateOutcome.Saved -> if (chapter.kind == KIND_SHORT) {
        UiMessage(R.string.gen_short_saved, listOf(chapter.title, attempts))
    } else {
        UiMessage(R.string.gen_saved, listOf(chapter.number, chapter.title, attempts))
    }
    is GenerateOutcome.Rejected -> UiMessage(
        R.string.gen_rejected,
        listOf(issues.joinToString(", ") { it.forRepair() }, attempts),
    )

    GenerateOutcome.NoWords -> UiMessage(R.string.gen_no_words)
    // A transport failure reads as "UnknownHostException: ..." raw, which tells a reader
    // nothing; the probe wording already says what to go fix, so reuse it.
    is GenerateOutcome.Failed -> failure?.let {
        UiMessage(probeFailureLabel(it), listOf(probeDetailOf(detail)))
    } ?: UiMessage(R.string.gen_failed, listOf(detail))
}
