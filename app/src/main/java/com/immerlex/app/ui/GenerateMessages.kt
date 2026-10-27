package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.ai.GenerateOutcome

fun GenerateOutcome.toUiMessage(): UiMessage = when (this) {
    is GenerateOutcome.Saved -> UiMessage(R.string.gen_saved, listOf(chapter.number, chapter.title, attempts))
    is GenerateOutcome.Rejected -> UiMessage(
        R.string.gen_rejected,
        listOf(issues.joinToString(", ") { it.forRepair() }, attempts),
    )

    GenerateOutcome.NoWords -> UiMessage(R.string.gen_no_words)
    is GenerateOutcome.Failed -> UiMessage(R.string.gen_failed, listOf(detail))
}
