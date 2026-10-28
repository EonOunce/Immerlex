package com.immerlex.core.bible

import com.immerlex.core.db.CharacterStateEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val NOW: Instant = Instant.ofEpochSecond(1_700_000_000)

private fun person(
    id: Long,
    name: String,
    role: String = "",
    status: String = "",
    first: Int = 1,
    last: Int = 1,
    authored: Boolean = false,
    conflictChapter: Int = 0,
    conflictText: String = "",
) = CharacterStateEntity(
    id = id,
    genre = "宫斗",
    name = name,
    role = role,
    status = status,
    firstChapterNumber = first,
    lastChapterNumber = last,
    authoredByUser = authored,
    conflictChapterNumber = conflictChapter,
    conflictText = conflictText,
    updatedAtSec = NOW.epochSecond,
)

/** 把 inserts/updates 合成一张按 id 索引的结果表，id=0 的新行按名字取。 */
private fun merged(outcome: CastMerger.Outcome): Map<String, CharacterStateEntity> =
    (outcome.inserts + outcome.updates).associateBy { it.name }

class CastMergerTest {

    @Test
    fun newcomerGetsItsRoleAndBothChapterBounds() {
        val outcome = CastMerger.merge(
            genre = "宫斗", current = emptyList(),
            appeared = listOf("落薇 · 皇后"), changes = emptyList(), chapterNumber = 4, now = NOW,
        )

        val row = outcome.inserts.single()
        assertEquals("落薇", row.name)
        assertEquals("皇后", row.role)
        assertEquals(4, row.firstChapterNumber)
        assertEquals(4, row.lastChapterNumber)
        assertEquals(0, outcome.updates.size)
    }

    @Test
    fun existingPersonOnlyAdvancesTheLastSeenChapter() {
        val existing = person(id = 1, name = "落薇", role = "皇后", status = "禁足冷宫", first = 2, last = 2)

        val outcome = CastMerger.merge("宫斗", listOf(existing), listOf("落薇"), emptyList(), 3, NOW)

        assertEquals(0, outcome.inserts.size)
        val row = outcome.updates.single()
        assertEquals(1, row.id)
        assertEquals(2, row.firstChapterNumber)
        assertEquals(3, row.lastChapterNumber)
        assertEquals("禁足冷宫", row.status)
    }

    @Test
    fun blankRoleIsFilledButNeverOverwritten() {
        val existing = person(id = 1, name = "刘禧", status = "近侍")
        val outcome = CastMerger.merge("宫斗", listOf(existing), listOf("刘禧 · 皇帝心腹"), emptyList(), 2, NOW)
        assertEquals("皇帝心腹", merged(outcome).getValue("刘禧").role)

        val again = CastMerger.merge("宫斗", listOf(existing.copy(role = "皇帝心腹")), listOf("刘禧 · 太监"), emptyList(), 3, NOW)
        assertEquals("身份不该被后来的写法改掉", "皇帝心腹", merged(again).getValue("刘禧").role)
    }

    @Test
    fun modelChangeUpdatesAnUntouchedStatus() {
        val existing = person(id = 1, name = "落薇", role = "皇后", status = "禁足冷宫", last = 2)

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), emptyList(),
            listOf(CharacterChange("落薇", "被放出冷宫，晋为贵妃")), 3, NOW,
        )

        val row = merged(outcome).getValue("落薇")
        assertEquals("被放出冷宫，晋为贵妃", row.status)
        assertEquals(3, row.lastChapterNumber)
        assertEquals(0, row.conflictChapterNumber)
    }

    @Test
    fun userWrittenStatusIsNeverOverwrittenOnlyFlagged() {
        val existing = person(id = 1, name = "落薇", status = "只想活着出宫", last = 2, authored = true)

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), emptyList(),
            listOf(CharacterChange("落薇", "当上太后")), 3, NOW,
        )

        val row = merged(outcome).getValue("落薇")
        assertEquals("读者写过的状态必须原样留着", "只想活着出宫", row.status)
        assertEquals(3, row.conflictChapterNumber)
        assertEquals("当上太后", row.conflictText)
    }

    @Test
    fun conflictClearsWhenTheStoryAgreesWithTheReader() {
        val existing = person(
            id = 1, name = "落薇", status = "只想活着出宫", last = 3,
            authored = true, conflictChapter = 2, conflictText = "当上太后",
        )

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), emptyList(),
            listOf(CharacterChange("落薇", "只想活着出宫")), 4, NOW,
        )

        val row = merged(outcome).getValue("落薇")
        assertEquals(0, row.conflictChapterNumber)
        assertEquals("", row.conflictText)
    }

    @Test
    fun rewritingAnOldChapterCannotResurrectSomeoneWhoDiedLater() {
        val existing = person(id = 1, name = "刘禧", status = "死在回廊", last = 5)

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), listOf("刘禧"),
            listOf(CharacterChange("刘禧", "掌印尚宫")), 3, NOW,
        )

        assertTrue("旧章重写不该改状态", outcome.updates.isEmpty())
    }

    @Test
    fun sameNameWrittenDifferentlyFoldsOntoTheSamePerson() {
        val existing = person(id = 1, name = "落薇", role = "皇后", status = "禁足冷宫", last = 2)

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), listOf("落薇（皇后）"),
            listOf(CharacterChange("落薇，皇帝之妻", "被放出冷宫")), 3, NOW,
        )

        assertEquals("同一个人不该插两行", 0, outcome.inserts.size)
        assertEquals("被放出冷宫", merged(outcome).getValue("落薇").status)
    }

    @Test
    fun laterChangeWinsWithinOneChapter() {
        val existing = person(id = 1, name = "落薇", status = "禁足冷宫", last = 2)

        val outcome = CastMerger.merge(
            "宫斗", listOf(existing), emptyList(),
            listOf(CharacterChange("落薇", "晋为贵妃"), CharacterChange("落薇", "赐死")), 3, NOW,
        )

        assertEquals("赐死", merged(outcome).getValue("落薇").status)
    }

    @Test
    fun repeatingTheSameChapterIsIdempotent() {
        val first = CastMerger.merge(
            "宫斗", emptyList(), listOf("落薇 · 皇后"),
            listOf(CharacterChange("落薇", "晋为贵妃")), 3, NOW,
        ).inserts.single().copy(id = 1)

        val second = CastMerger.merge(
            "宫斗", listOf(first), listOf("落薇 · 皇后"),
            listOf(CharacterChange("落薇", "晋为贵妃")), 3, NOW,
        )

        assertEquals(0, second.inserts.size)
        assertEquals("重写同一章不该产生任何写入", 0, second.updates.size)
    }

    @Test
    fun changeForAnUnseenPersonAddsThemAtThisChapter() {
        val outcome = CastMerger.merge(
            "宫斗", emptyList(), emptyList(),
            listOf(CharacterChange("新人甲 · 访客", "递上密信")), 6, NOW,
        )

        val row = outcome.inserts.single()
        assertEquals("新人甲", row.name)
        assertEquals("访客", row.role)
        assertEquals("递上密信", row.status)
        assertEquals(6, row.firstChapterNumber)
    }

    @Test
    fun newcomerWithAChangeInTheSameChapterIsWrittenOnce() {
        val outcome = CastMerger.merge(
            "宫斗", emptyList(), listOf("新人甲 · 访客"),
            listOf(CharacterChange("新人甲", "递上密信后离去")), 6, NOW,
        )

        assertEquals("插两行会撞 (genre, name) 唯一索引", 1, outcome.inserts.size)
        assertEquals("递上密信后离去", outcome.inserts.single().status)
        assertEquals("访客", outcome.inserts.single().role)
    }
}
