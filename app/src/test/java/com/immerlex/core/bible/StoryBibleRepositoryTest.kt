package com.immerlex.core.bible

import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.StoryBibleEntity
import com.immerlex.testsupport.FakeBibleEntryDao
import com.immerlex.testsupport.FakeCharacterStateDao
import com.immerlex.testsupport.FakeChapterDao
import com.immerlex.testsupport.FakeStoryBibleDao
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val NOW: Instant = Instant.ofEpochSecond(1_700_000_000)

private class Archive {
    val bibles = FakeStoryBibleDao()
    val entries = FakeBibleEntryDao()
    val cast = FakeCharacterStateDao()
    val chapters = FakeChapterDao()
    val repo = StoryBibleRepository(bibles, entries, cast, chapters)

    suspend fun addChapter(number: Int, title: String, summary: String, genre: String = "宫斗") {
        chapters.insert(
            ChapterEntity(
                number = number, title = title, genre = genre, createdAtSec = NOW.epochSecond,
                segmentsJson = "{}", summary = summary,
            ),
        )
    }
}

class StoryBibleRepositoryTest {

    @Test
    fun everyChapterStaysInTheRecapLayerOldestFirst() = runBlocking {
        val a = Archive()
        (1..5).forEach { a.addChapter(it, "标题$it", "梗概$it") }
        a.addChapter(6, "第六章", "   ")

        val context = a.repo.contextFor("宫斗", excludeChapterId = 0)

        assertEquals("空白梗概不该占 prompt 位置", 5, context.recaps.size)
        assertEquals((1..5).toList(), context.recaps.map { it.number })
        assertEquals("梗概1", context.recaps.first().summary)
    }

    @Test
    fun rewriteExcludesItsOwnRowFromTheRecaps() = runBlocking {
        val a = Archive()
        a.addChapter(3, "第三章 · 旧版", "旧梗概")
        val id = a.chapters.items.single().id

        val context = a.repo.contextFor("宫斗", excludeChapterId = id)

        assertTrue(context.recaps.isEmpty())
    }

    @Test
    fun legacyCastIsAdoptedOnceAndThenEmptied() = runBlocking {
        val a = Archive()
        a.bibles.upsert(
            StoryBibleEntity(
                genre = "宫斗", charactersJson = """["落薇 · 皇后","刘禧","落薇"]""",
                synopsis = "她走进雾里。", lastChapterNumber = 4, updatedAtSec = NOW.epochSecond,
            ),
        )

        val cast = a.repo.castOf("宫斗")

        assertEquals("重名只留一条", listOf("落薇", "刘禧"), cast.map { it.name })
        assertEquals("皇后", cast.first().role)
        assertEquals("搬过来的老角色不知道该出现在第几章", listOf(0, 0), cast.map { it.firstChapterNumber })
        assertEquals("旧列要清空，否则下次启动又搬一遍", "[]", a.bibles.forGenre("宫斗")!!.charactersJson)
        assertEquals("", a.bibles.forGenre("宫斗")!!.synopsis)
        assertEquals("搬完仍记得写到第几章", 4, a.repo.lastChapterNumber("宫斗"))

        a.repo.recordChapterOutcome("宫斗", 5, listOf("落薇 · 皇后"), emptyList(), NOW)
        assertEquals(2, a.cast.items.size)
    }

    @Test
    fun draftReplacesGeneratedLinesButKeepsWhatTheReaderWrote() = runBlocking {
        val a = Archive()
        a.repo.applyDraft(
            "宫斗",
            BibleDraft(tone = "冷", protagonist = "想活命", forbidden = listOf("不许重生"), cast = listOf(CastSeed("落薇", "皇后", "禁足冷宫"))),
            NOW,
        )
        val userLine = a.entries.items.first { it.section == "TONE" }.id
        a.repo.saveEntry(userLine, "克制", NOW)

        a.repo.applyDraft(
            "宫斗",
            BibleDraft(tone = "热闹", protagonist = "想复仇", ending = "团圆", cast = listOf(CastSeed("落薇", "皇后", "新处境"), CastSeed("新人", "访客", "递信"))),
            NOW,
        )

        val stored = a.repo.entriesOf("宫斗")
        assertEquals(listOf("克制"), stored.filter { it.section == "TONE" }.map { it.text })
        assertEquals(listOf("想复仇"), stored.filter { it.section == "PROTAGONIST" }.map { it.text })
        assertEquals(listOf("团圆"), stored.filter { it.section == "ENDING" }.map { it.text })
        assertEquals("重掷不该抹掉老角色的累积状态", "禁足冷宫", a.cast.items.first { it.name == "落薇" }.status)
        assertEquals("新面孔要补进来", listOf("落薇", "新人"), a.cast.items.map { it.name })
    }

    @Test
    fun entriesArePresentedInSectionOrderThenInsertionOrder() = runBlocking {
        val a = Archive()
        a.repo.addEntry("宫斗", BibleSection.FORBIDDEN, "不许重生", NOW)
        a.repo.addEntry("宫斗", BibleSection.TONE, "克制", NOW)
        a.repo.addEntry("宫斗", BibleSection.FORBIDDEN, "不许系统面板", NOW)

        val context = a.repo.contextFor("宫斗", 0)

        assertEquals(
            listOf(BibleSection.TONE, BibleSection.FORBIDDEN, BibleSection.FORBIDDEN),
            context.entries.map { it.section },
        )
        assertEquals(listOf("克制", "不许重生", "不许系统面板"), context.entries.map { it.text })
        assertTrue("读者手写的条目要带最高优先级标记", context.entries.all { it.authoredByUser })
    }

    @Test
    fun manualPersonRejectsBlanksAndDuplicates() = runBlocking {
        val a = Archive()

        assertFalse(a.repo.addCast("宫斗", "  ", "皇后", "", NOW))
        assertTrue(a.repo.addCast("宫斗", "落薇（皇后）", "皇后", "禁足", NOW))
        assertFalse("按折名判重", a.repo.addCast("宫斗", "落薇，皇帝之妻", "", "", NOW))
        assertEquals(1, a.cast.items.size)

        assertEquals("落薇", a.repo.deleteCast(a.cast.items.single().id))
        assertEquals(0, a.cast.items.size)
    }

    @Test
    fun editingAConflictedPersonSettlesIt() = runBlocking {
        val a = Archive()
        a.repo.addCast("宫斗", "落薇", "皇后", "只想活着出宫", NOW)
        a.repo.recordChapterOutcome("宫斗", 5, emptyList(), listOf(CharacterChange("落薇", "当上太后")), NOW)

        val row = a.cast.items.single()
        assertEquals("读者写的状态不被覆盖", "只想活着出宫", row.status)
        assertEquals(5, row.conflictChapterNumber)

        a.repo.saveCast(row.id, "落薇", "皇后", "当上太后", NOW)

        val settled = a.cast.items.single()
        assertEquals(0, settled.conflictChapterNumber)
        assertTrue("改过一次就视为读者所有", settled.authoredByUser)
    }

    @Test
    fun archiveNumberNeverGoesBackwards() = runBlocking {
        val a = Archive()

        a.repo.recordChapterOutcome("宫斗", 7, listOf("落薇"), emptyList(), NOW)
        assertEquals(7, a.repo.lastChapterNumber("宫斗"))

        a.repo.recordChapterOutcome("宫斗", 3, listOf("落薇"), emptyList(), NOW)
        assertEquals("重写旧章不该把章号退回去", 7, a.repo.lastChapterNumber("宫斗"))
    }

    @Test
    fun seedSurvivesAcrossRepositoryInstances() = runBlocking {
        val a = Archive()
        a.repo.saveSeed("宫斗", "  我想看宫斗  ", NOW)

        assertEquals("我想看宫斗", a.repo.seedOf("宫斗"))
        assertEquals("", a.repo.seedOf("科幻"))
        assertNotEquals(0L, a.bibles.forGenre("宫斗")!!.id)
    }
}
