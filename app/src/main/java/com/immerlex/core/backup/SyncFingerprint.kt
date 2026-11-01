package com.immerlex.core.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase

/**
 * 词、章、复习记录之外，同样只活在这台机器上的东西——素材、易错、故事档案、读书位置、室友。
 * 判定只看前三个数时，一台只改过这些内容的机器会被当成"没动过"，下载就把它们静默盖掉了。
 *
 * 这里把每张表的行数拼成一条字符串，只用来比相等，不用来解释"差在哪"：
 * 界面文案本来也只有一句「本机又动过」。表不存在按 0 算（旧备份里还没有这张表），
 * 于是同一个库在 Room 升版前后拼出来是同一个指纹。
 */
object SyncFingerprint {
    private val TABLES = listOf(
        "quiz_material",
        "tricky_words",
        "tricky_links",
        "story_bibles",
        "bible_entries",
        "character_states",
        "reading_positions",
        "daily_loop",
        "rm_profile",
        "rm_assets",
        "rm_events",
        "rm_ledger",
        "rm_habits",
        "rm_evidence",
        "rm_works",
        "rm_state",
        "rm_lines",
        "rm_daily",
        "rm_offers",
        "rm_pet_gifts",
    )

    /** 一台刚装好、什么都没写过的机器。[LocalState.isEmpty] 认的就是它。 */
    val EMPTY: String = render(emptyMap())

    fun render(counts: Map<String, Int>): String = TABLES.joinToString(",") { "$it=${counts[it] ?: 0}" }

    /** 本机正在用的库；调用方保证连接还开着。 */
    fun of(db: RoomDatabase): String = measure { db.openHelper.readableDatabase.query(it) }

    /** 一个已经拆出来的库文件：校验时那份、恢复前那份。 */
    fun of(file: SQLiteDatabase): String = measure { file.rawQuery(it, null) }

    private fun measure(sql: (String) -> Cursor): String {
        val present = mutableSetOf<String>()
        sql("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) present += cursor.getString(0)
        }
        val counts = mutableMapOf<String, Int>()
        TABLES.filter { it in present }.forEach { table ->
            sql("SELECT COUNT(*) FROM `$table`").use { cursor ->
                if (cursor.moveToFirst()) counts[table] = cursor.getInt(0)
            }
        }
        return render(counts)
    }
}
