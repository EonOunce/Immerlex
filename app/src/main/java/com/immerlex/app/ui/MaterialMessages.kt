package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.material.ImportOutcome
import com.immerlex.core.material.MaterialSyncFailure
import com.immerlex.core.material.MaterialSyncOutcome
import com.immerlex.core.material.PackFailure

fun ImportOutcome.toUiMessage(): UiMessage = when (this) {
    is ImportOutcome.Rejected -> UiMessage(packFailureLabel(failure))
    is ImportOutcome.Imported -> withDropped(
        base = when {
            added > 0 && updated() > 0 -> UiMessage(R.string.material_added_and_updated, listOf(added, updated()))
            added > 0 -> UiMessage(R.string.material_added, listOf(added))
            updated() > 0 -> UiMessage(R.string.material_updated, listOf(updated()))
            else -> UiMessage(R.string.material_unchanged)
        },
        dropped = droppedTotal(),
        badCollocations = badCollocations,
    )
}

fun MaterialSyncOutcome.toUiMessage(): UiMessage = when (this) {
    is MaterialSyncOutcome.Failed -> UiMessage(materialSyncFailureLabel(failure))
    is MaterialSyncOutcome.Fetched -> withDropped(
        base = when {
            added > 0 && updated > 0 ->
                UiMessage(R.string.material_fetched_added_and_updated, listOf(packs, added, updated))

            added > 0 -> UiMessage(R.string.material_fetched_added, listOf(packs, added))
            updated > 0 -> UiMessage(R.string.material_fetched_updated, listOf(packs, updated))
            else -> UiMessage(R.string.material_fetched_unchanged, listOf(packs))
        },
        dropped = droppedTotal(),
        badCollocations = badCollocations,
    )
}

/** 基数那句照旧，坏行与错搭配只是后缀：新增/更新数永远在最前面。 */
private fun withDropped(base: UiMessage, dropped: Int, badCollocations: Int): UiMessage = when {
    dropped > 0 && badCollocations > 0 ->
        UiMessage(R.string.material_with_dropped_and_bad_collocations, listOf(base, dropped, badCollocations))

    dropped > 0 -> UiMessage(R.string.material_with_dropped, listOf(base, dropped))
    badCollocations > 0 -> UiMessage(R.string.material_with_bad_collocations, listOf(base, badCollocations))
    else -> base
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
