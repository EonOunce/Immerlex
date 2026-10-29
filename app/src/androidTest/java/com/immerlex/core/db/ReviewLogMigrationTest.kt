package com.immerlex.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewLogMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v1RowsSurviveAndCarryTheLegacySentinel() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                """
                INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays)
                VALUES (7, 3, 'reader', 1700000000, 0, 2)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)
        db.query(
            "SELECT wordId, rating, preStability, preState, preDueEpochSec FROM review_logs",
            emptyArray<Any>(),
        ).use { cursor ->
            assertTrue("migrated table lost the v1 row", cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals(3, cursor.getInt(1))
            assertTrue("v1 rows cannot know the pre-rating card", cursor.isNull(2))
            assertEquals(FSRS_STATE_LEARNING, cursor.getInt(3))
            assertEquals(PRE_DUE_LEGACY, cursor.getLong(4))
        }
        db.close()
    }

    @Test
    fun v6RowsBecomeRecallAndNewColumnsStartEmpty() {
        helper.createDatabase(DB_V6, 6).apply {
            execSQL(
                """
                INSERT INTO review_logs
                    (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays, preState, preDueEpochSec)
                VALUES (7, 3, 'reader', 1700000000, 0, 2, 0, 0)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_V6, 7, true, MIGRATION_6_7)

        db.query("SELECT wordId, rating, kind, questionType FROM review_logs").use { cursor ->
            assertTrue("迁移弄丢了复习记录", cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("老记录都是先回忆再反馈", LOG_KIND_RECALL, cursor.getString(2))
            assertTrue("老记录没记题型", cursor.isNull(3))
        }

        db.execSQL(
            "INSERT INTO review_logs " +
                "(wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays, preState, preDueEpochSec, kind, questionType) " +
                "VALUES (8, 3, 'quiz', 1700000001, 1, 4, 0, 0, 'learn', 'EN_ZH')",
        )
        db.query("SELECT kind, questionType FROM review_logs WHERE wordId = 8").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(LOG_KIND_LEARN, cursor.getString(0))
            assertEquals("EN_ZH", cursor.getString(1))
        }
        db.close()
    }

    @Test
    fun v1LogChainsThroughSixMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                """
                INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, scheduledDays)
                VALUES (9, 2, 'reader', 1700000000, 0, 1)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            DB_V1,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )

        db.query("SELECT wordId, rating, kind, questionType FROM review_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9L, cursor.getLong(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(LOG_KIND_RECALL, cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
        const val DB_V6 = "review-migration-6.db"
        const val DB_V1 = "review-migration-1.db"
    }
}
