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
 * v16 -> v17 给血缘加一列指纹：词/章/记录之外那些表（素材、易错、故事档案、室友）也算本机独有的东西。
 * 老行必须留空串——空串与任何真实指纹都不相等，所以升级后第一次下载会多问一句。
 */
@RunWith(AndroidJUnit4::class)
class SyncFingerprintMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun anOldLineageRowGainsAnEmptyFingerprintInsteadOfAReadyOne() {
        helper.createDatabase(DB_V16, 16).apply {
            execSQL(
                "INSERT INTO sync_lineage (id, device, syncedName, syncedWords, syncedChapters, " +
                    "syncedReviews, syncedLastReviewId, lastUploadName, lastUploadSec, " +
                    "lastRestoreName, lastRestoreSec) " +
                    "VALUES (1, 'phone', 'immerlex-2026-11-01T080503-phone-v16.db.gz', 80, 4, 411, 411, " +
                    "'immerlex-2026-11-01T080503-phone-v16.db.gz', 1760000000, '', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V16, 17, true, MIGRATION_16_17)

        db.query(
            "SELECT device, syncedName, syncedWords, syncedLastReviewId, syncedExtras FROM sync_lineage WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("phone", cursor.getString(0))
            assertEquals("immerlex-2026-11-01T080503-phone-v16.db.gz", cursor.getString(1))
            assertEquals(80, cursor.getInt(2))
            assertEquals(411L, cursor.getLong(3))
            assertEquals("老行不能假装自己带指纹", "", cursor.getString(4))
        }

        db.execSQL(
            "UPDATE sync_lineage SET syncedExtras = 'quiz_material=6,tricky_words=0' WHERE id = 1",
        )
        db.query("SELECT syncedExtras FROM sync_lineage WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("quiz_material=6,tricky_words=0", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun otherTablesAreUntouchedAndV1ChainsAllTheWay() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            17,
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
        )

        db.query("SELECT title FROM chapters WHERE number = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第1章", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sync_lineage").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("一路升上来的库不能自带一份血缘", 0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V16 = "sync-fingerprint-migration-16.db"
        const val DB_V1 = "sync-fingerprint-migration-1.db"
    }
}
