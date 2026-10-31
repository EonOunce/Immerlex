package com.immerlex.core.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v8 -> v9 只加室友的新表，不动小说和复习的任何一列。
 * v9 -> v10 把固定商品目录换成每日包：老账本/事件/素材都留着，只拆掉 `rm_inventory`。
 * v10 -> v11 给档案加"多留的一句设定"和"它说的第一句"：老室友两列都是空串，不补演。
 * 全链一路跑到 v12，验证新加的 `quiz_material` 凭空出现时是空的，不给老用户编出素材。
 * 唯一索引是发钱的硬闸门：同一个 dedupeKey 第二次插入必须失败，靠这个挡住重复结算。
 */
@RunWith(AndroidJUnit4::class)
class RoommateMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v8KeepsChaptersAndAddsRoommateTables() {
        helper.createDatabase(DB_V8, 8).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary, kind) " +
                    "VALUES (2, '第2章', '宫斗', 1700000000, '{}', '灯亮着。', 'serial')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V8, 9, true, MIGRATION_8_9)

        db.query("SELECT title, summary FROM chapters WHERE number = 2").use { cursor ->
            assertTrue("迁移弄丢了连载", cursor.moveToFirst())
            assertEquals("第2章", cursor.getString(0))
            assertEquals("灯亮着。", cursor.getString(1))
        }
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        listOf(
            "rm_profile", "rm_assets", "rm_events", "rm_ledger",
            "rm_habits", "rm_evidence", "rm_inventory", "rm_works", "rm_state",
        ).forEach { assertTrue("缺少 $it", it in tables) }

        db.execSQL(
            "INSERT INTO rm_ledger (atSec, dayKey, amount, reason, dedupeKey, note) " +
                "VALUES (1700000000, 20000, 1, 'word', 'w:20000:lamp', 'lamp')",
        )
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO rm_ledger (atSec, dayKey, amount, reason, dedupeKey, note) " +
                    "VALUES (1700000001, 20000, 1, 'word', 'w:20000:lamp', 'lamp')",
            )
        }
        db.close()
    }

    @Test
    fun v9KeepsTheLedgerAndTradesInventoryForDailyContent() {
        helper.createDatabase(DB_V9, 9).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary, kind) " +
                    "VALUES (5, '第5章', '科幻', 1700000000, '{}', '灯还亮着。', 'serial')",
            )
            execSQL(
                "INSERT INTO rm_state (id, lastSettledDay, lastEpisodeDay, lastAiDay, lastVisitSec) " +
                    "VALUES (1, 20000, 19998, 19997, 1700000000)",
            )
            execSQL(
                "INSERT INTO rm_ledger (atSec, dayKey, amount, reason, dedupeKey, note) " +
                    "VALUES (1700000000, 20000, 2, 'word', 'w:20000:lamp', 'lamp')",
            )
            execSQL(
                "INSERT INTO rm_inventory (itemId, acquiredAtSec, used) VALUES ('candy', 1700000000, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V9, 10, true, MIGRATION_9_10)

        db.query("SELECT title, summary FROM chapters WHERE number = 5").use { cursor ->
            assertTrue("迁移弄丢了连载", cursor.moveToFirst())
            assertEquals("第5章", cursor.getString(0))
            assertEquals("灯还亮着。", cursor.getString(1))
        }
        db.query("SELECT lastSettledDay, linebookAiDone FROM rm_state WHERE id = 1").use { cursor ->
            assertTrue("迁移弄丢了结算水位线", cursor.moveToFirst())
            assertEquals(20000L, cursor.getLong(0))
            assertEquals("老室友还没问过模型", 0, cursor.getInt(1))
        }
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        listOf("rm_lines", "rm_daily", "rm_offers", "rm_pet_gifts").forEach {
            assertTrue("缺少 $it", it in tables)
        }
        assertTrue("固定商品目录应该已经拆掉", "rm_inventory" !in tables)

        db.query("SELECT amount FROM rm_ledger WHERE dedupeKey = 'w:20000:lamp'").use { cursor ->
            assertTrue("迁移弄丢了已经发出去的钱", cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO rm_ledger (atSec, dayKey, amount, reason, dedupeKey, note) " +
                    "VALUES (1700000001, 20000, 2, 'word', 'w:20000:lamp', 'lamp')",
            )
        }
        db.close()
    }

    @Test
    fun v10KeepsTheRoommateAndGainsSettingColumns() {
        helper.createDatabase(DB_V10, 10).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary, kind) " +
                    "VALUES (7, '第7章', '科幻', 1700000000, '{}', '灯又亮了。', 'serial')",
            )
            execSQL(
                "INSERT INTO rm_profile (id, name, nature, pace, face, persona, createdAtSec) " +
                    "VALUES (1, '阿灰', 'direct', 'slow', 'open', '有话就说', 1700000000)",
            )
            execSQL(
                "INSERT INTO rm_ledger (atSec, dayKey, amount, reason, dedupeKey, note) " +
                    "VALUES (1700000000, 20000, 3, 'word', 'w:20000:lamp', 'lamp')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V10, 11, true, MIGRATION_10_11)

        db.query("SELECT title FROM chapters WHERE number = 7").use { cursor ->
            assertTrue("迁移弄丢了连载", cursor.moveToFirst())
            assertEquals("第7章", cursor.getString(0))
        }
        db.query("SELECT name, persona, setting, introLine FROM rm_profile WHERE id = 1").use { cursor ->
            assertTrue("迁移弄丢了室友", cursor.moveToFirst())
            assertEquals("阿灰", cursor.getString(0))
            assertEquals("有话就说", cursor.getString(1))
            assertEquals("老室友没有多留的设定", "", cursor.getString(2))
            assertEquals("老室友不补演第一句", "", cursor.getString(3))
        }
        db.query("SELECT amount FROM rm_ledger WHERE dedupeKey = 'w:20000:lamp'").use { cursor ->
            assertTrue("迁移弄丢了已经发出去的钱", cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
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
            12,
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
        )

        db.query("SELECT title FROM chapters WHERE number = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第1章", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM rm_profile").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("老用户不该凭空多出室友", 0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM quiz_material").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("老用户不该凭空多出素材", 0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V8 = "roommate-migration-8.db"
        const val DB_V9 = "roommate-migration-9.db"
        const val DB_V10 = "roommate-migration-10.db"
        const val DB_V1 = "roommate-migration-1.db"
    }
}
