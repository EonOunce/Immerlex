package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.material.ImportOutcome
import com.immerlex.core.material.PackFailure

fun ImportOutcome.toUiMessage(): UiMessage = when (this) {
    is ImportOutcome.Rejected -> UiMessage(packFailureLabel(failure))
    is ImportOutcome.Imported -> when {
        droppedTotal() > 0 && badCollocations > 0 ->
            UiMessage(R.string.material_imported_with_bad_collocations, listOf(written, droppedTotal(), badCollocations))

        droppedTotal() > 0 -> UiMessage(R.string.material_imported, listOf(written, droppedTotal()))
        badCollocations > 0 ->
            UiMessage(R.string.material_imported_bad_collocations, listOf(written, badCollocations))

        else -> UiMessage(R.string.material_imported_clean, listOf(written))
    }
}

fun packFailureLabel(failure: PackFailure): Int = when (failure) {
    PackFailure.NOT_JSON -> R.string.material_not_json
    PackFailure.NOT_ARRAY -> R.string.material_not_list
    PackFailure.EMPTY -> R.string.material_empty_file
}
