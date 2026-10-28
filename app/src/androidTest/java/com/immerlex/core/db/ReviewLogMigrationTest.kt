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

    private companion object {
        const val DB = "migration-test.db"
    }
}
