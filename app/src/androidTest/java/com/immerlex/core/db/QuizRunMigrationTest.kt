package com.immerlex.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v17 -> v18 只加两张表：做完的测验成绩、每天一个"此刻大概能想起几个词"的影子。
 * 升级上来的老库这两张表必须是空的——战绩页要能分得清"还没测过"和"测过但对错了 0 题"，
 * 不能凭空造出历史。词、章节、复习记录、没做完的会话一个字节都不动。
 */
@RunWith(AndroidJUnit4::class)
class QuizRunMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v17KeepsEverythingAndGainsTwoEmptyTables() {
        helper.createDatabase(DB_V17, 17).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('harbour', '', '港湾', '', 'manual', 1700000000, 1, 2, 0, 6)",
            )
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, " +
                    "scheduledDays, kind) VALUES (1, 3, 'quiz', 1700003600, 4, 6, 'recall')",
            )
            execSQL("INSERT INTO quiz_session (mode, payload) VALUES ('DUE', '{\"due\":true}')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V17, 18, true, MIGRATION_17_18)

        for (table in listOf("quiz_runs", "recall_snapshots")) {
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("老用户升级不该凭空多出一段历史", 0, cursor.getInt(0))
            }
        }
        db.query(
            "SELECT kind, scheduledDays FROM review_logs WHERE wordId = 1",
        ).use { cursor ->
            assertTrue("迁移弄丢了复习记录", cursor.moveToFirst())
            assertEquals("recall", cursor.getString(0))
            assertEquals(6, cursor.getInt(1))
        }
        db.query("SELECT payload FROM quiz_session WHERE mode = 'DUE'").use { cursor ->
            assertTrue("没做完的那一组题不能因为升级被清掉", cursor.moveToFirst())
            assertEquals("{\"due\":true}", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun aDayOnlyKeepsItsHighestSample() {
        helper.createDatabase(DB_V17, 17).close()
        val db = helper.runMigrationsAndValidate(DB_V17, 18, true, MIGRATION_17_18)

        // 与 RecallSnapshotDao 同一句式：占住这一天，之后只有更高才抬得上去。
        db.execSQL("INSERT OR IGNORE INTO recall_snapshots (epochDay, canRecall, mastered, atSec) VALUES (20000, 12, 40, 1)")
        db.execSQL(
            "UPDATE recall_snapshots SET canRecall = 9, mastered = 41, atSec = 2 " +
                "WHERE epochDay = 20000 AND canRecall < 9",
        )
        db.query("SELECT canRecall, mastered, atSec FROM recall_snapshots WHERE epochDay = 20000").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("低的那次不该把高点抹掉", 12, cursor.getInt(0))
            assertEquals(40, cursor.getInt(1))
            assertEquals(1L, cursor.getLong(2))
        }
        db.execSQL(
            "UPDATE recall_snapshots SET canRecall = 15, mastered = 44, atSec = 3 " +
                "WHERE epochDay = 20000 AND canRecall < 15",
        )
        db.query("SELECT canRecall, mastered FROM recall_snapshots WHERE epochDay = 20000").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
            assertEquals("跟着高点一起走的是同一时刻算出的掌握数", 44, cursor.getInt(1))
        }
        db.close()
    }

    @Test
    fun v1ChainsAllTheWay() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            18,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
        )

        db.query("SELECT title FROM chapters WHERE number = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第1章", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM quiz_runs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V17 = "quiz-run-migration-17.db"
        const val DB_V1 = "quiz-run-migration-1.db"
    }
}
