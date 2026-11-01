package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.material.ImportOutcome
import com.immerlex.core.material.MaterialSyncFailure
import com.immerlex.core.material.MaterialSyncOutcome
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

fun MaterialSyncOutcome.toUiMessage(): UiMessage = when (this) {
    is MaterialSyncOutcome.Failed -> UiMessage(materialSyncFailureLabel(failure))
    is MaterialSyncOutcome.Fetched -> when {
        droppedTotal() > 0 && badCollocations > 0 -> UiMessage(
            R.string.material_fetched_with_bad_collocations, listOf(packs, words, droppedTotal(), badCollocations),
        )

        droppedTotal() > 0 -> UiMessage(R.string.material_fetched_dropped, listOf(packs, words, droppedTotal()))
        badCollocations > 0 -> UiMessage(R.string.material_fetched_bad_collocations, listOf(packs, words, badCollocations))
        else -> UiMessage(R.string.material_fetched, listOf(packs, words))
    }
}

fun materialSyncFailureLabel(failure: MaterialSyncFailure): Int = when (failure) {
    MaterialSyncFailure.NOT_CONFIGURED -> R.string.material_fetch_not_configured
    MaterialSyncFailure.BAD_URL -> R.string.material_fetch_bad_url
    MaterialSyncFailure.UNREACHABLE -> R.string.material_fetch_unreachable
    MaterialSyncFailure.TIMEOUT -> R.string.material_fetch_timeout
    MaterialSyncFailure.TLS -> R.string.material_fetch_tls
    MaterialSyncFailure.HTTP -> R.string.material_fetch_http
    MaterialSyncFailure.MANIFEST -> R.string.material_fetch_manifest
    MaterialSyncFailure.CHECKSUM -> R.string.material_fetch_checksum
    MaterialSyncFailure.PACK -> R.string.material_fetch_pack
    MaterialSyncFailure.LOCAL -> R.string.material_fetch_local
}

fun packFailureLabel(failure: PackFailure): Int = when (failure) {
    PackFailure.NOT_JSON -> R.string.material_not_json
    PackFailure.NOT_ARRAY -> R.string.material_not_list
    PackFailure.EMPTY -> R.string.material_empty_file
}
