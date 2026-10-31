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
 * v12 -> v13 只加一张未完成的测验会话表：到期组与提前复习组各留一份，互不覆盖。
 * 已有的词、复习记录和出题素材一个字都不动。
 */
@RunWith(AndroidJUnit4::class)
class QuizSessionMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v12KeepsEverythingAndGainsAnEmptySessionTable() {
        helper.createDatabase(DB_V12, 12).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('harbour', '', '港湾', '', 'manual', 1700000000, 1, 2, 0, 6)",
            )
            execSQL(
                "INSERT INTO quiz_material (wordId, definition, example, distractors, createdAtSec) " +
                    "VALUES (1, 'a place where ships stay', '船停在港湾。', '[]', 1700003600)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V12, 13, true, MIGRATION_12_13)

        db.query("SELECT COUNT(*) FROM quiz_session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("老用户不该凭空多出一组题", 0, cursor.getInt(0))
        }
        db.query("SELECT definition FROM quiz_material WHERE wordId = 1").use { cursor ->
            assertTrue("迁移弄丢了出题素材", cursor.moveToFirst())
            assertEquals("a place where ships stay", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun dueAndEarlySessionsAreStoredApart() {
        helper.createDatabase(DB_V12, 12).close()
        val db = helper.runMigrationsAndValidate(DB_V12, 13, true, MIGRATION_12_13)

        db.execSQL("INSERT INTO quiz_session (mode, payload) VALUES ('DUE', '{\"due\":true}')")
        db.execSQL("INSERT INTO quiz_session (mode, payload) VALUES ('EARLY', '{\"early\":true}')")
        db.query("SELECT COUNT(*) FROM quiz_session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        db.execSQL("DELETE FROM quiz_session WHERE mode = 'DUE'")
        db.query("SELECT mode FROM quiz_session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("提前复习那一组不该被到期组连坐删掉", "EARLY", cursor.getString(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V12 = "quiz-session-migration-12.db"
    }
}
