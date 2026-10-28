package com.immerlex.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v0.3 的两张档案表与 seedText 列必须是"搬数据"而不是"重建库"。 */
@RunWith(AndroidJUnit4::class)
class StoryBibleMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v2ArchiveSurvivesAndTheNewTablesAreUsable() {
        val legacy = """["落薇 · 皇后","刘禧"]"""
        helper.createDatabase(DB_V2, 2).apply {
            execSQL(
                "INSERT INTO story_bibles (genre, charactersJson, synopsis, lastChapterNumber, updatedAtSec) " +
                    "VALUES ('宫斗', '$legacy', '她走进雾里。', 4, 1700000000)",
            )
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (4, '第四章 · 回廊', '宫斗', 1700000000, '{}', '灯灭在回廊尽头。')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V2, 3, true, MIGRATION_2_3)

        db.query("SELECT genre, charactersJson, synopsis, lastChapterNumber, seedText FROM story_bibles").use { cursor ->
            assertTrue("迁移把旧档案弄丢了", cursor.moveToFirst())
            assertEquals("宫斗", cursor.getString(0))
            assertEquals(legacy, cursor.getString(1))
            assertEquals("她走进雾里。", cursor.getString(2))
            assertEquals(4, cursor.getInt(3))
            assertEquals("旧版本没有这一列，补默认空串", "", cursor.getString(4))
        }
        db.query("SELECT number, summary FROM chapters").use { cursor ->
            assertTrue("迁移不该动章节表", cursor.moveToFirst())
            assertEquals(4, cursor.getInt(0))
        }

        db.execSQL(
            "INSERT INTO bible_entries (genre, section, text, ordinal, authoredByUser, updatedAtSec) " +
                "VALUES ('宫斗', 'TONE', '克制', 0, 1, 1700000000)",
        )
        db.execSQL(
            "INSERT INTO character_states (genre, name, role, status, firstChapterNumber, lastChapterNumber, " +
                "authoredByUser, conflictChapterNumber, conflictText, updatedAtSec) " +
                "VALUES ('宫斗', '落薇', '皇后', '', 0, 0, 0, 0, '', 1700000000)",
        )
        try {
            db.execSQL(
                "INSERT INTO character_states (genre, name, role, status, firstChapterNumber, lastChapterNumber, " +
                    "authoredByUser, conflictChapterNumber, conflictText, updatedAtSec) " +
                    "VALUES ('宫斗', '落薇', '', '', 0, 0, 0, 0, '', 1700000000)",
            )
            fail("同名人物的唯一索引没建上")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // 索引存在，仓库层的按名折重才有兜底。
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughBothMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays) " +
                    "VALUES (7, 3, 'reader', 1700000000, 0, 2)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V1, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT wordId, preStability FROM review_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM bible_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V2 = "bible-migration-2.db"
        const val DB_V1 = "bible-migration-1.db"
    }
}
