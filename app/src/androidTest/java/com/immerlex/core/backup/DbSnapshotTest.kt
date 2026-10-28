package com.immerlex.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immerlex.core.db.ImmerlexDatabase
import java.io.File
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 备份能不能"换回本机"全看这份校验：它一旦放过坏文件，用户学到一半的库就被换成空库或半截库。
 * 所以这里逐条验它拒绝的理由，而不是只验它会通过。
 */
@RunWith(AndroidJUnit4::class)
class DbSnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = ImmerlexDatabase.create(context)
    private val dir = File(context.cacheDir, "snapshot-test").apply {
        deleteRecursively()
        mkdirs()
    }

    @Test
    fun aRealSnapshotUnpacksIntoTheSameCounts() {
        val gzip = DbSnapshot.take(db, context, dir)
        assertTrue(gzip.length() > 0L)

        val unpacked = DbSnapshot.unpack(gzip, File(dir, "out.db"), DbSnapshot.version(db))

        assertTrue(unpacked.issues.toString(), unpacked.usable)
        assertEquals(countOf("words"), unpacked.words.toLong())
        assertEquals(countOf("chapters"), unpacked.chapters.toLong())
    }

    @Test
    fun aPlainTextFileIsNotABackup() {
        val file = File(dir, "plain.gz").apply { writeBytes("Immerlex ".repeat(8).toByteArray()) }

        assertIssues(SnapshotIssue.NOT_GZIP, file)
    }

    @Test
    fun anEmptyDownloadIsNotABackup() {
        val file = File(dir, "tiny.gz").apply { writeBytes(byteArrayOf(0x1f, 0x8b.toByte(), 0)) }

        assertIssues(SnapshotIssue.EMPTY, file)
    }

    @Test
    fun gzipThatHidesSomethingElseThanADatabaseIsRefused() {
        val file = File(dir, "garbage.db.gz")
        GZIPOutputStream(file.outputStream()).use { it.write("this is not a database".repeat(8).toByteArray()) }

        assertIssues(SnapshotIssue.NOT_SQLITE, file)
    }

    @Test
    fun aNewerDatabaseCannotBeReadByAnOlderApp() {
        val snapshot = DbSnapshot.take(db, context, dir)
        val extracted = File(dir, "future.db")
        DbSnapshot.unpack(snapshot, extracted, DbSnapshot.version(db))
        val newer = DbSnapshot.version(db) + 1
        SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { it.execSQL("PRAGMA user_version = $newer") }
        val future = File(dir, "future.db.gz")
        GZIPOutputStream(future.outputStream()).use { out -> extracted.inputStream().use { it.copyTo(out) } }

        val problems = DbSnapshot.unpack(future, File(dir, "future.back.db"), DbSnapshot.version(db)).issues

        assertTrue(problems.toString(), problems.any { it.kind == SnapshotIssue.TOO_NEW })
        assertEquals(newer.toString() + " > " + DbSnapshot.version(db), problems.first { it.kind == SnapshotIssue.TOO_NEW }.detail)
    }

    private fun assertIssues(expected: SnapshotIssue, file: File) {
        val problems = DbSnapshot.unpack(file, File(dir, "target.db"), DbSnapshot.version(db)).issues
        assertEquals(listOf(expected), problems.map { it.kind })
    }

    private fun countOf(table: String): Long =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
}
