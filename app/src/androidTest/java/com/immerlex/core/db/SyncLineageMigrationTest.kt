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
 * v13 -> v14 只加一张一行表：这台机器上次和云端对齐时本机长什么样。
 * 有了它，"取回会冲掉多少本机东西"才答得出来；没配网盘的老用户凭空多一行空值，什么也不问。
 * 全链一路跑到 v14，验证老库升上来时血缘是空的——宁可问一句，不猜。
 */
@RunWith(AndroidJUnit4::class)
class SyncLineageMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v13KeepsEverythingAndGainsOneBlankLineageRow() {
        helper.createDatabase(DB_V13, 13).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('harbour', '', '港湾', '', 'manual', 1700000000, 1, 2, 0, 6)",
            )
            execSQL(
                "INSERT INTO review_logs (wordId, rating, source, reviewEpochSec, elapsedDays, " +
                    "scheduledDays, preState, preDueEpochSec, kind, questionType) " +
                    "VALUES (1, 3, 'quiz', 1700000000, 0, 6, 1, 1700003600, 'recall', 'EN_ZH')",
            )
            execSQL("INSERT INTO quiz_session (mode, payload) VALUES ('DUE', '{\"due\":true}')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V13, 14, true, MIGRATION_13_14)

        db.query("SELECT COUNT(*) FROM sync_lineage").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("没同步过的老用户不该有一条假装对齐过的记录", 0, cursor.getInt(0))
        }
        db.query("SELECT payload FROM quiz_session WHERE mode = 'DUE'").use { cursor ->
            assertTrue("迁移弄丢了没做完的测验", cursor.moveToFirst())
            assertEquals("{\"due\":true}", cursor.getString(0))
        }
        db.query("SELECT kind FROM review_logs WHERE wordId = 1").use { cursor ->
            assertTrue("迁移弄丢了复习记录", cursor.moveToFirst())
            assertEquals("recall", cursor.getString(0))
        }

        db.execSQL("INSERT INTO sync_lineage (id, device) VALUES (1, 'pad')")
        db.query(
            "SELECT syncedName, syncedWords, syncedReviews, lastUploadSec FROM sync_lineage WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("没上传过的列都得是空值，不能是 0 秒这种看着像时间的东西", "", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0L, cursor.getLong(3))
        }
        db.close()
    }

    @Test
    fun onlyOneDeviceCanClaimTheRow() {
        helper.createDatabase(DB_V13, 13).close()
        val db = helper.runMigrationsAndValidate(DB_V13, 14, true, MIGRATION_13_14)

        db.execSQL(
            "INSERT INTO sync_lineage (id, device, syncedName, syncedWords, syncedLastReviewId) " +
                "VALUES (1, 'phone', 'immerlex-2026-10-31T213000-phone-v14.db.gz', 80, 411)",
        )
        db.query("SELECT device, syncedName, syncedLastReviewId FROM sync_lineage").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("phone", cursor.getString(0))
            assertEquals("immerlex-2026-10-31T213000-phone-v14.db.gz", cursor.getString(1))
            assertEquals(411L, cursor.getLong(2))
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughAllMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            14,
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
        )

        db.query("SELECT title FROM chapters WHERE number = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第1章", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM quiz_session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM sync_lineage").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("一路升上来的库不能自带一份血缘", 0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V13 = "sync-lineage-migration-13.db"
        const val DB_V1 = "sync-lineage-migration-1.db"
    }
}
