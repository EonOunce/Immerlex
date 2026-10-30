package com.immerlex.core.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DbFileSwapTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun installReplacesDatabaseAndDropsOldSidecars() {
        val target = tmp.newFile("immerlex.db").apply { writeText("old") }
        File(target.path + "-wal").writeText("old-wal")
        File(target.path + "-shm").writeText("old-shm")
        val replacement = tmp.newFile("restore.db").apply { writeText("new") }

        DbFileSwap.install(target, replacement)

        assertEquals("new", target.readText())
        assertFalse(File(target.path + "-wal").exists())
        assertFalse(File(target.path + "-shm").exists())
        assertFalse(File(target.path + ".prev").exists())
        assertFalse(File(target.path + ".new").exists())
    }

    @Test
    fun missingReplacementLeavesTheOldDatabaseUntouched() {
        val target = tmp.newFile("immerlex.db").apply { writeText("old") }
        File(target.path + "-wal").writeText("old-wal")
        val replacement = File(tmp.root, "missing.db")

        assertThrows(Exception::class.java) { DbFileSwap.install(target, replacement) }

        assertEquals("old", target.readText())
        assertEquals("old-wal", File(target.path + "-wal").readText())
    }

    @Test
    fun recoverPutsTheOldDatabaseBackAfterAnInterruptedSwap() {
        val target = File(tmp.root, "immerlex.db")
        val prev = File(target.path + ".prev").apply { writeText("old") }
        File(target.path + "-wal.prev").writeText("old-wal")

        DbFileSwap.recover(target)

        assertEquals("old", target.readText())
        assertEquals("old-wal", File(target.path + "-wal").readText())
        assertFalse(prev.exists())
    }

    @Test
    fun recoverKeepsTheNewDatabaseWhenTheSwapHadFinished() {
        val target = tmp.newFile("immerlex.db").apply { writeText("new") }
        val prev = File(target.path + ".prev").apply { writeText("old") }

        DbFileSwap.recover(target)

        assertEquals("new", target.readText())
        assertFalse(prev.exists())
    }

    @Test
    fun recoverOnAFreshInstallDoesNothing() {
        val target = File(tmp.root, "immerlex.db")

        DbFileSwap.recover(target)

        assertFalse(target.exists())
    }

    @Test
    fun installWorksWhenThereWasNoDatabaseYet() {
        val target = File(tmp.root, "immerlex.db")
        val replacement = tmp.newFile("restore.db").apply { writeText("new") }

        DbFileSwap.install(target, replacement)

        assertTrue(target.exists())
        assertEquals("new", target.readText())
    }
}
