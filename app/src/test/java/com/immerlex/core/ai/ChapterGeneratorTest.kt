package com.immerlex.core.ai

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.ChapterRecap
import com.immerlex.core.db.StoryBibleDao
import com.immerlex.core.db.StoryBibleEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.schedule.TodayQueue
import com.immerlex.core.schedule.WordQueue
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeChapterDao : ChapterDao {
    val items = mutableListOf<ChapterEntity>()
    private var nextId = 1L
    override suspend fun insert(entity: ChapterEntity): Long {
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun latest(genre: String) = items.filter { it.genre == genre }.maxByOrNull { it.number }
    override suspend fun recentRecaps(genre: String, limit: Int) =
        items.filter { it.genre == genre }.sortedByDescending { it.number }.take(limit)
            .map { ChapterRecap(it.number, it.title, it.summary) }
}

private class FakeBibleDao : StoryBibleDao {
    val items = mutableMapOf<String, StoryBibleEntity>()
    private var nextId = 1L
    override suspend fun forGenre(genre: String) = items[genre]
    override suspend fun upsert(entity: StoryBibleEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items[entity.genre] = entity.copy(id = id)
        return id
    }
}

private class FakeQueue(private val today: TodayQueue) : WordQueue {
    override suspend fun todaysQueue(now: Instant, dailyNewLimit: Int) = today
}

private class FakeClient(private val responses: List<String>) : LlmClient {
    val prompts = mutableListOf<String>()
    var calls = 0

    override suspend fun complete(system: String, user: String): String {
        prompts += user
        val response = responses[minOf(calls, responses.lastIndex)]
        calls++
        if (response == THROW) throw LlmException(500, "上游错误")
        return response
    }

    override suspend fun probe(): String? = null

    companion object { const val THROW = "\u0000throw" }
}

private fun bank(words: List<String>) = words.map { WordEntity(word = it, source = "test", createdAtSec = 0) }

private fun generator(
    responses: List<String>,
    fixture: Fixture = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3),
    chapters: FakeChapterDao = FakeChapterDao(),
    bible: FakeBibleDao = FakeBibleDao(),
) = Triple(
    ChapterGenerator(
        queue = FakeQueue(TodayQueue(reviewWords = emptyList(), newWords = bank(fixture.words))),
        chapterDao = chapters,
        bibleDao = bible,
        clientFor = { FakeClient(responses) },
        maxRepairs = 2,
    ),
    chapters,
    bible,
)

class ChapterGeneratorTest {

    @Test
    fun savesValidChapterAndBible() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val (gen, chapters, bible) = generator(listOf(encode(f.doc)))

        val outcome = gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30)

        assertTrue(outcome is GenerateOutcome.Saved)
        assertEquals(1, chapters.items.size)
        assertEquals(1, chapters.items.single().number)
        assertEquals("她走进雾里。", bible.forGenre("宫斗")?.synopsis)
        assertEquals(1, bible.forGenre("宫斗")?.lastChapterNumber)
    }

    @Test
    fun repairsOnceThenSaves() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        var captured: FakeClient? = null
        val chapters = FakeChapterDao()
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(f.words))),
            chapterDao = chapters,
            bibleDao = FakeBibleDao(),
            clientFor = { FakeClient(listOf("这不是 JSON", encode(f.doc))).also { captured = it } },
        )

        val outcome = gen.generate("武侠", Density.DENSE, dailyNewLimit = 30)

        assertEquals(2, (outcome as GenerateOutcome.Saved).attempts)
        assertEquals(2, captured!!.calls)
        assertTrue(captured!!.prompts[1].contains("unparseable_json"))
        assertTrue(captured!!.prompts[1].contains("武侠"))
    }

    @Test
    fun rejectsAfterMaxRepairs() = runBlocking {
        val (gen, chapters, _) = generator(listOf("garbage"))

        val outcome = gen.generate("科幻", Density.DENSE, dailyNewLimit = 30)

        assertEquals(3, (outcome as GenerateOutcome.Rejected).attempts)
        assertEquals(0, chapters.items.size)
    }

    @Test
    fun stopsOnNetworkError() = runBlocking {
        val (gen, chapters, _) = generator(listOf(FakeClient.THROW))

        val outcome = gen.generate("科幻", Density.DENSE, dailyNewLimit = 30)

        assertTrue(outcome is GenerateOutcome.Failed)
        assertEquals(0, chapters.items.size)
    }

    @Test
    fun failsWhenBankIsEmpty() = runBlocking {
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), emptyList())),
            chapterDao = FakeChapterDao(),
            bibleDao = FakeBibleDao(),
            clientFor = { FakeClient(listOf("{}")) },
        )

        val outcome = gen.generate("科幻", Density.DENSE, dailyNewLimit = 30)

        assertEquals(GenerateOutcome.NoWords, outcome)
    }

    @Test
    fun promptListsEveryWordAndDensityTier() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val client = FakeClient(listOf(encode(f.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(f.words))),
            chapterDao = FakeChapterDao(),
            bibleDao = FakeBibleDao(),
            clientFor = { client },
        )

        gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30)

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("35-50"))
        f.words.forEach { assertTrue("missing $it", prompt.contains(it)) }
        assertNull(client.probe())
        assertTrue(prompt.contains("本章为该类型的第一章"))
    }

    @Test
    fun nextChapterPromptCarriesRecapAndCast() = runBlocking {
        val first = fixture(
            paragraphs = 6, blocks = 2, wordsPerParagraph = 3,
            title = "第一章 · 起雾", summary = "她提着灯走进雾里。", characters = listOf("落薇 · 皇后"),
        )
        val second = fixture(
            paragraphs = 6, blocks = 2, wordsPerParagraph = 3,
            title = "第二章 · 回廊", summary = "灯灭在回廊尽头。", characters = listOf("刘禧 · 皇帝近侍"),
        )
        val chapters = FakeChapterDao()
        val bible = FakeBibleDao()
        val responses = ArrayDeque(listOf(encode(first.doc), encode(second.doc)))
        val clients = mutableListOf<FakeClient>()
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(first.words))),
            chapterDao = chapters,
            bibleDao = bible,
            clientFor = { FakeClient(listOf(responses.removeFirst())).also { clients += it } },
        )

        assertTrue(gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30) is GenerateOutcome.Saved)
        assertTrue(gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30) is GenerateOutcome.Saved)

        val nextPrompt = clients[1].prompts.single()
        assertTrue("missing recap", nextPrompt.contains("第一章 · 起雾"))
        assertTrue("missing summary", nextPrompt.contains("她提着灯走进雾里。"))
        assertTrue("missing cast", nextPrompt.contains("落薇 · 皇后"))
        assertTrue("wrong number", nextPrompt.contains("编号第2章"))
        assertEquals(
            "newest chapter's cast first",
            listOf("刘禧 · 皇帝近侍", "落薇 · 皇后"),
            decodeCharacters(bible.forGenre("宫斗")!!.charactersJson),
        )
        assertEquals(2, chapters.items.size)
    }

    @Test
    fun castListStaysBoundedAndKeepsNewcomers() = runBlocking {
        val old = (1..12).map { "旧角$it" }
        val bible = FakeBibleDao()
        bible.upsert(
            StoryBibleEntity(
                genre = "科幻",
                charactersJson = old.joinToString(",", "[", "]") { "\"$it\"" },
                lastChapterNumber = 1,
                updatedAtSec = 0,
            ),
        )
        val fresh = fixture(
            paragraphs = 6, blocks = 2, wordsPerParagraph = 3,
            title = "第二章", summary = "新面孔", characters = listOf("新人甲 · 访客"),
        )
        val chapters = FakeChapterDao()
        val client = FakeClient(listOf(encode(fresh.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(fresh.words))),
            chapterDao = chapters,
            bibleDao = bible,
            clientFor = { client },
        )

        assertTrue(gen.generate("科幻", Density.DENSE, dailyNewLimit = 30) is GenerateOutcome.Saved)
        assertTrue(client.prompts.single().contains("旧角1、旧角2"))

        val stored = decodeCharacters(bible.forGenre("科幻")!!.charactersJson)
        assertEquals(12, stored.size)
        assertEquals("新人甲 · 访客", stored.first())
        assertFalse("旧角12 ages out", stored.contains("旧角12"))
        assertEquals(2, chapters.items.single().number)
    }

    @Test
    fun recapsAreTruncatedToThreeChaptersOldestFirst() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val chapters = FakeChapterDao()
        repeat(5) { n ->
            chapters.insert(
                ChapterEntity(
                    number = n + 1, title = "标题${n + 1}", genre = "科幻", createdAtSec = 0,
                    segmentsJson = "{}", summary = "梗概${n + 1}",
                ),
            )
        }
        val bible = FakeBibleDao()
        bible.upsert(StoryBibleEntity(genre = "科幻", lastChapterNumber = 5, updatedAtSec = 0))
        val client = FakeClient(listOf(encode(f.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(f.words))),
            chapterDao = chapters,
            bibleDao = bible,
            clientFor = { client },
        )

        gen.generate("科幻", Density.DENSE, dailyNewLimit = 30)

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("编号第6章"))
        assertTrue(prompt.contains("前情提要（由旧到新）"))
        assertFalse("chapter 1 fell out of the window", prompt.contains("梗概1"))
        assertFalse("chapter 2 fell out of the window", prompt.contains("梗概2"))
        val positions = listOf("梗概3", "梗概4", "梗概5").map { prompt.indexOf(it) }
        assertEquals(listOf(true, true, true), positions.map { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    private fun decodeCharacters(array: String): List<String> =
        Json.decodeFromString<List<String>>(array)
}
