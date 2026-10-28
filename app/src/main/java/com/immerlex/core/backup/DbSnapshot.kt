package com.immerlex.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 校验没过的那一种原因：措辞在 strings.xml，这里只带机器码和它读到的值。 */
enum class SnapshotIssue { EMPTY, NOT_GZIP, NOT_SQLITE, NO_VERSION, TOO_NEW, CHECK_FAILED, MISSING_TABLES }

data class SnapshotProblem(val kind: SnapshotIssue, val detail: String = "")

/** 一份解过包的备份：sqlite 文件本体，加上它过不过得了关、里面有多少东西。 */
data class Unpacked(
    val sqlite: File,
    val issues: List<SnapshotProblem>,
    val words: Int = 0,
    val chapters: Int = 0,
) {
    val usable: Boolean get() = issues.isEmpty()
}

/**
 * 一致性快照固定走"checkpoint 把 WAL 压回主文件 → 复制 → gzip"三步，绝不把正在写入的
 * SQLite 文件直接交给同步通道（docs/adr/0006 第 5 条）。
 * 快照在本机先校验一遍再上传：真撞上了写入中途，坏的是一次上传失败的备份，
 * 而不是云端一份"看起来好好的"坏库。
 */
object DbSnapshot {
    const val DB_NAME = "immerlex.db"

    /** v1 起就存在的最老三张表：连这些都没有的文件不是本应用的库。 */
    private val REQUIRED_TABLES = listOf("words", "chapters", "review_logs")
    private const val SQLITE_HEADER = "SQLite format 3"

    /** @return gzip 后的快照，调用方上传完自己删。 */
    fun take(db: RoomDatabase, context: Context, dir: File): File {
        // WAL 模式下最近的写还在 -wal 里，不压回去复制到的就是一个旧库。
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        val source = context.getDatabasePath(DB_NAME)
        if (!source.exists()) throw WebDavException(BackupFailure.LOCAL, "${source.name} missing")
        dir.mkdirs()
        val gzip = File(dir, "$DB_NAME.gz")
        GZIPOutputStream(gzip.outputStream()).use { out -> source.inputStream().use { it.copyTo(out) } }
        return gzip
    }

    fun version(db: RoomDatabase): Int = db.openHelper.writableDatabase.version

    /** 解包并校验：gzip 头 → SQLite 头 → 版本不高于本机 → integrity_check → 必需表齐。 */
    fun unpack(gzip: File, into: File, currentVersion: Int): Unpacked {
        val issues = mutableListOf<SnapshotProblem>()
        if (!gzip.exists() || gzip.length() < 20) {
            return Unpacked(into, listOf(SnapshotProblem(SnapshotIssue.EMPTY)))
        }
        val magic = ByteArray(2)
        gzip.inputStream().use { it.read(magic) }
        if (magic[0] != 0x1f.toByte() || magic[1] != 0x8b.toByte()) {
            return Unpacked(into, listOf(SnapshotProblem(SnapshotIssue.NOT_GZIP)))
        }

        into.parentFile?.mkdirs()
        GZIPInputStream(gzip.inputStream()).use { source -> into.outputStream().use { source.copyTo(it) } }

        val head = ByteArray(SQLITE_HEADER.length)
        into.inputStream().use { it.read(head) }
        if (String(head, Charsets.ISO_8859_1) != SQLITE_HEADER) {
            return Unpacked(into, listOf(SnapshotProblem(SnapshotIssue.NOT_SQLITE)))
        }

        val readable = SQLiteDatabase.openDatabase(into.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val fileVersion = readLong(readable, "PRAGMA user_version")
            when {
                fileVersion <= 0 -> issues += SnapshotProblem(SnapshotIssue.NO_VERSION)
                fileVersion > currentVersion ->
                    issues += SnapshotProblem(SnapshotIssue.TOO_NEW, "$fileVersion > $currentVersion")
            }

            val check = readString(readable, "PRAGMA integrity_check")
            if (check != "ok") issues += SnapshotProblem(SnapshotIssue.CHECK_FAILED, check.take(120))

            val tables = mutableListOf<String>()
            readable.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            val missing = REQUIRED_TABLES.filterNot { it in tables }
            if (missing.isNotEmpty()) issues += SnapshotProblem(SnapshotIssue.MISSING_TABLES, missing.joinToString())
            if (issues.isNotEmpty()) return Unpacked(into, issues)

            return Unpacked(
                sqlite = into,
                issues = emptyList(),
                words = readLong(readable, "SELECT COUNT(*) FROM words").toInt(),
                chapters = readLong(readable, "SELECT COUNT(*) FROM chapters").toInt(),
            )
        } finally {
            readable.close()
        }
    }

    /**
     * 覆盖本地库。调用方必须先 `db.close()`，并且知道应用需要重开一次：
     * 正在跑的 Room 连接不能换文件，热切换会在下一次查询时崩。
     */
    fun install(context: Context, sqlite: File) {
        val target = context.getDatabasePath(DB_NAME)
        // deleteDatabase 连 -wal / -shm 一起清掉；留着旧的 WAL 会把旧数据盖回新库上。
        SQLiteDatabase.deleteDatabase(target)
        target.parentFile?.mkdirs()
        sqlite.copyTo(target, overwrite = true)
    }

    private fun readLong(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    private fun readString(db: SQLiteDatabase, sql: String): String =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
}
