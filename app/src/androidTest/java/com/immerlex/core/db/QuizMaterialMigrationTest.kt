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
 * v11 -> v12 只加一张表：一个词的出题素材存一行，考过就不再问模型。
 * 已有词库、测验记录一个字都不动，主键保证同一个词只有一行。
 */
@RunWith(AndroidJUnit4::class)
class QuizMaterialMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v11KeepsTheQuizHistoryAndGainsOneRowPerWord() {
        helper.createDatabase(DB_V11, 11).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('affect', '', '影响', 'to have an effect on', 'manual', 1700000000, 1, 2, 0, 6)",
            )
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, " +
                    "scheduledDays, preState, preDueEpochSec, kind, questionType) " +
                    "VALUES (1, 3, 'quiz', 1700000000, 0, 6, 1, 1700003600, 'recall', 'EN_ZH')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V11, 12, true, MIGRATION_11_12)

        db.query("SELECT COUNT(*) FROM quiz_material").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("老用户不该凭空多出素材", 0, cursor.getInt(0))
        }
        db.query("SELECT questionType FROM review_logs WHERE wordId = 1").use { cursor ->
            assertTrue("迁移弄丢了测验记录", cursor.moveToFirst())
            assertEquals("EN_ZH", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO quiz_material (wordId, definition, example, distractors, createdAtSec) " +
                "VALUES (1, 'to have an effect on', '这会影响结果。', " +
                "'[{\"w\":\"effect\",\"zh\":\"效果\"},{\"w\":\"afford\",\"zh\":\"买得起\"}]', 1700003600)",
        )
        db.query("SELECT COUNT(*) FROM quiz_material").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        try {
            db.execSQL("INSERT INTO quiz_material (wordId, createdAtSec) VALUES (1, 1700003601)")
            fail("一个词只能留一份素材")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
        }

        db.close()
    }

    @Test
    fun defaultsCoverWordsTheModelOnlyHalfAnswered() {
        helper.createDatabase(DB_V11, 11).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('effect', '', '效果', '', 'manual', 1700000000, 0, 0, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V11, 12, true, MIGRATION_11_12)
        db.execSQL("INSERT INTO quiz_material (wordId, createdAtSec) VALUES (1, 1700003600)")

        db.query("SELECT definition, example, distractors FROM quiz_material WHERE wordId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("[]", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        const val DB_V11 = "quiz-material-migration-11.db"
    }
}
