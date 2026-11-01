package com.immerlex.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指纹只用来比相等，所以它的全部要求是：同一组数永远得出同一串、缺表按 0、
 * 任何一张表行数变了这串就变。
 */
class SyncFingerprintTest {

    @Test
    fun theSameCountsRenderTheSameStringInTheSameOrder() {
        val counts = mapOf("quiz_material" to 3, "tricky_words" to 2)
        assertEquals(SyncFingerprint.render(counts), SyncFingerprint.render(counts.toList().reversed().toMap()))
    }

    @Test
    fun aTableMissingFromAnOlderBackupCountsAsZero() {
        val withoutTable = SyncFingerprint.render(mapOf("quiz_material" to 0))
        val empty = SyncFingerprint.render(emptyMap())
        assertEquals(empty, withoutTable)
    }

    @Test
    fun theEmptyFingerprintIsWhatAFreshInstallProduces() {
        assertTrue(SyncFingerprint.EMPTY.contains("quiz_material=0"))
        assertTrue(SyncFingerprint.EMPTY.contains("rm_ledger=0"))
    }

    @Test
    fun changingOneRowOfOneTableChangesTheFingerprint() {
        assertNotEquals(
            SyncFingerprint.render(mapOf("tricky_links" to 4)),
            SyncFingerprint.render(mapOf("tricky_links" to 5)),
        )
        // 两张表各一行与一张表两行不是一回事。
        assertNotEquals(
            SyncFingerprint.render(mapOf("tricky_words" to 1, "tricky_links" to 1)),
            SyncFingerprint.render(mapOf("tricky_words" to 2)),
        )
    }

    @Test
    fun tablesOutsideTheFingerprintDoNotChangeIt() {
        assertEquals(
            SyncFingerprint.render(mapOf("quiz_material" to 1)),
            SyncFingerprint.render(mapOf("quiz_material" to 1, "backup_runs" to 9, "sync_lineage" to 1)),
        )
    }
}
