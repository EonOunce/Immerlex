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

/**
 * v1.0 的两张表只能"新长出来"，不能"重建库"：老用户的连续天数全靠 `review_logs` 里的原话，
 * 一旦迁移顺手清了这张表，连续天数会当场归零。见 docs/adr/0006 第 2 条。
 */
@RunWith(AndroidJUnit4::class)
class DailyLoopMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v3HistorySurvivesAndTheNewTablesAreUsable() {
        helper.createDatabase(DB_V3, 3).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('ephemeral', '/ɪˈfemərəl/', '短暂的', 'lasting for a very short time', " +
                    "'csv', 1700000000, 1, 4, 1, 12)",
            )
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays, " +
                    "preState, preDueEpochSec) VALUES (1, 3, 'reader', 1700000000, 2, 12, 1, 1699900000)",
            )
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (4, '第四章 · 回廊', '宫斗', 1700000000, '{}', '灯灭在回廊尽头。')",
            )
            execSQL(
                "INSERT INTO bible_entries (genre, section, text, ordinal, authoredByUser, updatedAtSec) " +
                    "VALUES ('宫斗', 'TONE', '克制', 0, 1, 1700000000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V3, 4, true, MIGRATION_3_4)

        // 打卡事实的唯一来源：迁移必须原样留着这些行，否则升级当天连续天数就没了。
        db.query("SELECT word, translation, reps FROM words").use { cursor ->
            assertTrue("迁移弄丢了词库", cursor.moveToFirst())
            assertEquals("ephemeral", cursor.getString(0))
            assertEquals("短暂的", cursor.getString(1))
            assertEquals(4, cursor.getInt(2))
        }
        db.query("SELECT wordId, rating, source FROM review_logs").use { cursor ->
            assertTrue("迁移弄丢了复习记录", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(3, cursor.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM chapters").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bible_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        db.execSQL("INSERT INTO daily_loop (epochDay, dueCleared, makeupConsumed) VALUES (20762, 1, 0)")
        try {
            db.execSQL("INSERT INTO daily_loop (epochDay, dueCleared, makeupConsumed) VALUES (20762, 0, 1)")
            fail("一天一行的主键没建上")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // REPLACE 语义靠这条主键兜底，服务层一天只该有一行。
        }
        db.query("SELECT dueCleared, makeupConsumed FROM daily_loop WHERE epochDay = 20762").use { cursor ->
            assertTrue("同名的第二条不该把第一条塞进去", cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }

        db.execSQL(
            "INSERT INTO backup_runs (atSec, ok, remoteName, bytes, detail) " +
                "VALUES (1700000000, 1, 'immerlex-2026-10-28.db.gz', 4321, '')",
        )
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_backup_runs_atSec'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("设置页取最新一条要靠这个索引", 1, cursor.getInt(0))
            }
        db.close()
    }

    @Test
    fun v1RowsChainThroughAllThreeMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays) " +
                    "VALUES (7, 3, 'reader', 1700000000, 0, 2)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )

        db.query("SELECT wordId, preDueEpochSec FROM review_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals("v1 的老记录没有快照，必须是哨兵值", -1L, cursor.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM daily_loop").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM backup_runs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V3 = "daily-loop-migration-3.db"
        const val DB_V1 = "daily-loop-migration-1.db"
    }
}
