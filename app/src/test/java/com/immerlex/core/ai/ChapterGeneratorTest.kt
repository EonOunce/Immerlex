package com.immerlex.core.ai

import com.immerlex.core.bible.BibleLine
import com.immerlex.core.bible.BibleSection
import com.immerlex.core.bible.CastLine
import com.immerlex.core.bible.CharacterChange
import com.immerlex.core.bible.RecapLine
import com.immerlex.core.bible.StoryContext
import com.immerlex.core.bible.StoryContextSource
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.WordEntity
import com.immerlex.core.schedule.TodayQueue
import com.immerlex.core.schedule.WordQueue
import com.immerlex.testsupport.FakeChapterDao
import com.immerlex.testsupport.FakeWordDao
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    override suspend fun probe(): ProbeResult = ProbeResult(ProbeFailure.NONE)

    companion object {
        const val THROW = "\u0000throw"
    }
}

private class FakeStory(
    private val context: StoryContext = StoryContext(),
    private val lastNumber: Int = 0,
) : StoryContextSource {
    val outcomes = mutableListOf<Triple<Int, List<String>, List<CharacterChange>>>()
    var lastExcludedChapterId = -1L

    override suspend fun contextFor(genre: String, excludeChapterId: Long): StoryContext {
        lastExcludedChapterId = excludeChapterId
        return context
    }

    override suspend fun lastChapterNumber(genre: String) = lastNumber

    override suspend fun recordChapterOutcome(
        genre: String,
        chapterNumber: Int,
        appeared: List<String>,
        changes: List<CharacterChange>,
        now: Instant,
    ) {
        outcomes += Triple(chapterNumber, appeared, changes)
    }
}

private fun bank(words: List<String>) = words.map { WordEntity(word = it, source = "test", createdAtSec = 0) }

private fun chapterRow(number: Int, genre: String, title: String, summary: String) = ChapterEntity(
    number = number,
    title = title,
    genre = genre,
    createdAtSec = 0,
    segmentsJson = "{}",
    summary = summary,
)

private class Harness(
    val chapters: FakeChapterDao = FakeChapterDao(),
    val words: FakeWordDao = FakeWordDao(),
    val story: FakeStory = FakeStory(),
)

private fun generator(
    responses: List<String>,
    fixture: Fixture,
    harness: Harness = Harness(),
    dailyNewLimit: Int = 30,
): Pair<ChapterGenerator, FakeClient> {
    val client = FakeClient(responses)
    val gen = ChapterGenerator(
        queue = FakeQueue(TodayQueue(reviewWords = emptyList(), newWords = bank(fixture.words))),
        chapterDao = harness.chapters,
        story = harness.story,
        wordDao = harness.words,
        clientFor = { client },
        maxRepairs = 2,
    )
    return gen to client
}

class ChapterGeneratorTest {

    @Test
    fun savesValidChapterAndRecordsItsOutcome() = runBlocking {
        val f = fixture(
            paragraphs = 6, blocks = 2, wordsPerParagraph = 3,
            characters = listOf("落薇 · 皇后"),
            changes = listOf(CharacterChange("落薇", "被放出冷宫，晋为贵妃")),
        )
        val harness = Harness()
        val (gen, _) = generator(listOf(encode(f.doc)), f, harness)

        val outcome = gen.generate("宫斗", Density.MEDIUM, dailyNewLimit = 30)

        assertTrue(outcome is GenerateOutcome.Saved)
        assertEquals(1, harness.chapters.items.size)
        assertEquals(1, harness.chapters.items.single().number)
        val saved = harness.story.outcomes.single()
        assertEquals(1, saved.first)
        assertEquals(listOf("落薇 · 皇后"), saved.second)
        assertEquals(listOf(CharacterChange("落薇", "被放出冷宫，晋为贵妃")), saved.third)
    }

    @Test
    fun repairsOnceThenSaves() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val client = FakeClient(listOf("这不是 JSON", encode(f.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), bank(f.words))),
            chapterDao = FakeChapterDao(),
            story = FakeStory(),
            wordDao = FakeWordDao(),
            clientFor = { client },
        )

        val outcome = gen.generate("武侠", Density.MEDIUM, dailyNewLimit = 30)

        assertEquals(2, (outcome as GenerateOutcome.Saved).attempts)
        assertEquals(2, client.calls)
        assertTrue(client.prompts[1].contains("unparseable_json"))
        assertTrue(client.prompts[1].contains("武侠"))
    }

    @Test
    fun rejectsAfterMaxRepairs() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness()
        val (gen, _) = generator(listOf("garbage"), f, harness)

        val outcome = gen.generate("科幻", Density.MEDIUM, dailyNewLimit = 30)

        assertEquals(3, (outcome as GenerateOutcome.Rejected).attempts)
        assertEquals(0, harness.chapters.items.size)
        assertEquals(0, harness.story.outcomes.size)
    }

    @Test
    fun stopsOnNetworkError() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness()
        val (gen, _) = generator(listOf(FakeClient.THROW), f, harness)

        val outcome = gen.generate("科幻", Density.MEDIUM, dailyNewLimit = 30)

        val failed = outcome as GenerateOutcome.Failed
        assertEquals(ProbeFailure.HTTP_ERROR, failed.failure)
        assertEquals("上游错误", failed.detail)
        assertEquals(0, harness.chapters.items.size)
        assertEquals(0, harness.story.outcomes.size)
    }

    @Test
    fun failsWhenBankIsEmpty() = runBlocking {
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), emptyList())),
            chapterDao = FakeChapterDao(),
            story = FakeStory(),
            wordDao = FakeWordDao(),
            clientFor = { FakeClient(listOf("{}")) },
        )

        assertEquals(GenerateOutcome.NoWords, gen.generate("科幻", Density.MEDIUM, dailyNewLimit = 30))
    }

    @Test
    fun promptListsEveryWordAndItsDensityTier() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 6)
        val (gen, client) = generator(listOf(encode(f.doc)), f)

        gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30)

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("35-50"))
        f.words.forEach { assertTrue("missing $it", prompt.contains(it)) }
        assertTrue("档案为空时不该编出前情", prompt.contains("本章为该类型的第一章"))
        assertFalse(prompt.contains("【故事档案】"))
        assertFalse(prompt.contains("【全书梗概"))
    }

    @Test
    fun defaultTargetLengthIsAboutAThousandChars() = runBlocking {
        val f = fixture(paragraphs = 25, blocks = 2, wordsPerParagraph = 1)
        val (gen, client) = generator(listOf(encode(f.doc)), f)

        val outcome = gen.generate("宫斗", Density.MEDIUM, dailyNewLimit = 30)

        assertTrue(client.prompts.single().contains("正文约 1000 个汉字"))
        assertTrue(outcome is GenerateOutcome.Saved)
    }

    @Test
    fun smallBankDowngradesTheDensityAskedOfTheModel() = runBlocking {
        val f = fixture(paragraphs = 4, blocks = 3, wordsPerParagraph = 1)
        val (gen, client) = generator(listOf(encode(f.doc)), f)

        val outcome = gen.generate("宫斗", Density.DENSE, dailyNewLimit = 30)

        assertTrue("4 个词撑不起密档", client.prompts.first().contains("10-15"))
        assertTrue("校验必须按降档后的密度判", outcome is GenerateOutcome.Saved)
        assertEquals(1, client.calls)
    }

    @Test
    fun promptCarriesAllThreeContinuityLayers() = runBlocking {
        val story = StoryContext(
            entries = listOf(
                BibleLine(BibleSection.TONE, "克制、冷，不写大团圆", false),
                BibleLine(BibleSection.PROTAGONIST, "落薇只想活着出宫", true),
                BibleLine(BibleSection.FORBIDDEN, "不许出现重生", false),
            ),
            cast = listOf(
                CastLine("落薇", "皇后", "被禁足冷宫，手里握着密信", authoredByUser = true),
                CastLine("刘禧", "皇帝近侍", "替皇帝盯着尚宫局", conflictChapter = 5, conflictText = "被贬为庶人"),
            ),
            recaps = listOf(RecapLine(4, "回廊", "灯灭在回廊尽头。")),
        )
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val (gen, client) = generator(listOf(encode(f.doc)), f, Harness(story = FakeStory(story)))

        gen.generate("宫斗", Density.MEDIUM, dailyNewLimit = 30)

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("【故事档案】"))
        assertTrue(prompt.contains("基调：克制、冷，不写大团圆"))
        assertTrue(prompt.contains("主角与目标：落薇只想活着出宫（读者设定，务必遵守）"))
        assertTrue(prompt.contains("禁止事项：不许出现重生"))
        assertTrue(prompt.contains("【人物当前状态】"))
        assertTrue(prompt.contains("落薇（皇后）：被禁足冷宫，手里握着密信（读者设定，务必遵守）"))
        assertTrue(prompt.contains("注意：第5章把它写成了「被贬为庶人」"))
        assertTrue(prompt.contains("【全书梗概（由旧到新）】"))
        assertTrue(prompt.contains("- 第4章《回廊》：灯灭在回廊尽头。"))
        assertTrue(prompt.contains("changes 只填本章里处境发生变化的人物"))
    }

    @Test
    fun everyEarlierChapterStaysInThePrompt() = runBlocking {
        val story = StoryContext(recaps = (1..5).map { RecapLine(it, "标题$it", "梗概$it") })
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val (gen, client) = generator(listOf(encode(f.doc)), f, Harness(story = FakeStory(story)))

        gen.generate("科幻", Density.MEDIUM, dailyNewLimit = 30)

        val prompt = client.prompts.single()
        (1..5).forEach { assertTrue("第$it 章不该被丢掉", prompt.contains("梗概$it")) }
        val positions = (1..5).map { prompt.indexOf("梗概$it") }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun chapterNumberFollowsTheArchiveWhenTheLastChapterWasDeleted() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness(story = FakeStory(lastNumber = 7))
        val (gen, _) = generator(listOf(encode(f.doc)), f, harness)

        val outcome = gen.generate("宫斗", Density.MEDIUM, dailyNewLimit = 30)

        assertEquals(8, (outcome as GenerateOutcome.Saved).chapter.number)
    }

    @Test
    fun chapterNumberFollowsTheTableWhenTheArchiveIsBehind() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness()
        harness.chapters.insert(chapterRow(7, "宫斗", "第七章", "梗概7"))
        val (gen, _) = generator(listOf(encode(f.doc)), f, harness)

        val outcome = gen.generate("宫斗", Density.MEDIUM, dailyNewLimit = 30)

        assertEquals(8, (outcome as GenerateOutcome.Saved).chapter.number)
        assertEquals(8, harness.chapters.items.maxOf { it.number })
    }

    @Test
    fun rewriteReusesThisChaptersWordsAndExcludesItsOwnRecap() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness()
        f.words.forEach { harness.words.insert(WordEntity(word = it, source = "test", createdAtSec = 0)) }
        harness.chapters.insert(chapterRow(3, "宫斗", "第三章 · 旧版", "旧梗概").copy(segmentsJson = encode(f.doc)))
        val id = harness.chapters.items.single().id
        val fresh = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3, title = "第三章 · 新版")
        val client = FakeClient(listOf(encode(fresh.doc)))
        val gen = ChapterGenerator(
            // 今日的队列是空的：重写照常成稿，说明它用的是这一章自己的词。
            queue = FakeQueue(TodayQueue(emptyList(), emptyList())),
            chapterDao = harness.chapters,
            story = harness.story,
            wordDao = harness.words,
            clientFor = { client },
        )

        val outcome = gen.rewrite(id, Density.MEDIUM)

        assertEquals(3, (outcome as GenerateOutcome.Saved).chapter.number)
        assertEquals("重写不该新增一行", 1, harness.chapters.items.size)
        assertEquals(id, harness.chapters.items.single().id)
        assertEquals("第三章 · 新版", harness.chapters.items.single().title)
        f.words.forEach { assertTrue("重写要沿用本章的词", client.prompts.single().contains(it)) }
        assertEquals("重写不该把自己算作前情", id, harness.story.lastExcludedChapterId)
        assertEquals("重写不该动学习进度", 0, harness.words.items.values.sumOf { it.reps })
    }

    @Test
    fun rejectedRewriteLeavesTheOriginalChapterAlone() = runBlocking {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val harness = Harness()
        f.words.forEach { harness.words.insert(WordEntity(word = it, source = "test", createdAtSec = 0)) }
        harness.chapters.insert(chapterRow(3, "宫斗", "第三章 · 旧版", "旧梗概").copy(segmentsJson = encode(f.doc)))
        val (gen, _) = generator(listOf("garbage"), f, harness)

        assertTrue(gen.rewrite(harness.chapters.items.single().id, Density.MEDIUM) is GenerateOutcome.Rejected)
        assertEquals("第三章 · 旧版", harness.chapters.items.single().title)
        assertEquals(0, harness.story.outcomes.size)
    }

    @Test
    fun shortStoryCarriesTheToneAndEveryWord() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val (gen, client) = generator(listOf(encode(f.doc)), f)

        val outcome = gen.generateShort(ShortPlan(tone = ShortTone.LUSH))

        assertTrue(outcome is GenerateOutcome.Saved)
        val prompt = client.prompts.single()
        assertTrue(prompt.contains(ShortTone.LUSH.brief))
        assertTrue(prompt.contains("1500–2500"))
        f.words.forEach { assertTrue("短篇没带上 $it", prompt.contains(it)) }
        assertFalse("短篇不该读故事档案", prompt.contains("【故事档案】"))
    }

    @Test
    fun shortIsStoredAsShortAndLeavesTheBibleAlone() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val harness = Harness(story = FakeStory(context = StoryContext(recaps = listOf(RecapLine(1, "旧章", "旧梗概")))))
        val (gen, _) = generator(listOf(encode(f.doc)), f, harness)

        val outcome = gen.generateShort(ShortPlan(tone = ShortTone.EVERYMAN)) as GenerateOutcome.Saved

        assertEquals(KIND_SHORT, outcome.chapter.kind)
        assertEquals(0, outcome.chapter.number)
        assertEquals(KIND_SHORT, harness.chapters.items.single().kind)
        assertEquals("短篇是自足的，不该回写人物状态", 0, harness.story.outcomes.size)
    }

    @Test
    fun shortStoryAsksForAtMostFifteenWords() = runBlocking {
        val bankFixture = fixture(paragraphs = 20, blocks = 3, wordsPerParagraph = 1)
        val doc = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val (gen, client) = generator(listOf(encode(doc.doc)), bankFixture)

        val outcome = gen.generateShort(ShortPlan(tone = ShortTone.COLD))

        assertTrue(outcome is GenerateOutcome.Saved)
        val prompt = client.prompts.single()
        assertTrue(prompt.contains(bankFixture.words[14]))
        assertFalse("第 16 个词不该进短篇", prompt.contains(bankFixture.words[15]))
    }

    @Test
    fun shortRewriteKeepsItsKindAndTone() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val harness = Harness()
        f.words.forEach { harness.words.insert(WordEntity(word = it, source = "test", createdAtSec = 0)) }
        harness.chapters.insert(
            chapterRow(0, "COLD", "旧短篇", "").copy(segmentsJson = encode(f.doc), kind = KIND_SHORT),
        )
        val id = harness.chapters.items.single().id
        val fresh = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1, title = "新短篇")
        val client = FakeClient(listOf(encode(fresh.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), emptyList())),
            chapterDao = harness.chapters,
            story = harness.story,
            wordDao = harness.words,
            clientFor = { client },
        )

        val outcome = gen.rewrite(id, Density.MEDIUM) as GenerateOutcome.Saved

        assertEquals(KIND_SHORT, outcome.chapter.kind)
        assertEquals("COLD", outcome.chapter.genre)
        assertEquals("新短篇", harness.chapters.items.single().title)
        assertEquals("重写不该新增一行", 1, harness.chapters.items.size)
        assertTrue(client.prompts.single().contains(ShortTone.COLD.brief))
        assertEquals(0, harness.story.outcomes.size)
    }

    @Test
    fun aShortWithNoIdeaLeavesTheSubjectToTheModel() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val (gen, client) = generator(listOf(encode(f.doc)), f)

        gen.generateShort(ShortPlan(tone = ShortTone.COLD))

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("写什么由你定"))
        assertTrue(prompt.contains("腔调："))
        assertFalse(prompt.contains("读者自己写的"))
    }

    @Test
    fun ownToneAndSubjectReplaceThePresetAndAreStoredWithTheStory() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val harness = Harness()
        val (gen, client) = generator(listOf(encode(f.doc)), f, harness)
        val plan = ShortPlan(
            toneText = "写在深夜的长途货车上，句子要短，结尾不停在道理上",
            idea = "一个司机把迷路的眼镜猴送到渔港",
        )

        val outcome = gen.generateShort(plan) as GenerateOutcome.Saved

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("腔调（读者自己写的，务必遵守）：${plan.toneText}"))
        assertTrue(prompt.contains("读者指定了这篇写什么：${plan.idea}"))
        assertFalse("自己写了腔调就不该再叠加预设那一段", prompt.contains(ShortTone.EVERYMAN.brief))
        assertEquals(CUSTOM_TONE, outcome.chapter.genre)
        assertEquals(plan.toneText, harness.chapters.items.single().toneText)
        assertEquals(plan.idea, harness.chapters.items.single().idea)
    }

    @Test
    fun shortRewriteReusesTheOwnToneAndSubject() = runBlocking {
        val f = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1)
        val harness = Harness()
        f.words.forEach { harness.words.insert(WordEntity(word = it, source = "test", createdAtSec = 0)) }
        harness.chapters.insert(
            chapterRow(0, CUSTOM_TONE, "旧短篇", "")
                .copy(
                    segmentsJson = encode(f.doc),
                    kind = KIND_SHORT,
                    toneText = "句子要短，结尾不停在道理上",
                    idea = "一个司机把迷路的眼镜猴送到渔港",
                ),
        )
        val id = harness.chapters.items.single().id
        val fresh = fixture(paragraphs = 15, blocks = 3, wordsPerParagraph = 1, title = "新短篇")
        val client = FakeClient(listOf(encode(fresh.doc)))
        val gen = ChapterGenerator(
            queue = FakeQueue(TodayQueue(emptyList(), emptyList())),
            chapterDao = harness.chapters,
            story = harness.story,
            wordDao = harness.words,
            clientFor = { client },
        )

        val outcome = gen.rewrite(id, Density.MEDIUM) as GenerateOutcome.Saved

        val prompt = client.prompts.single()
        assertTrue("重写该沿用读者自己写的腔调", prompt.contains("句子要短，结尾不停在道理上"))
        assertTrue("重写该沿用同一篇的主题", prompt.contains("一个司机把迷路的眼镜猴送到渔港"))
        assertEquals(CUSTOM_TONE, outcome.chapter.genre)
    }
}
