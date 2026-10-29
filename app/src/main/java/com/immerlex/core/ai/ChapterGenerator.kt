package com.immerlex.core.ai

import com.immerlex.core.bible.StoryContext
import com.immerlex.core.bible.StoryContextSource
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.schedule.WordQueue
import com.immerlex.core.word.mastery
import com.immerlex.core.word.toSegmentToken
import java.time.Instant

sealed interface GenerateOutcome {
    data class Saved(val chapter: ChapterEntity, val attempts: Int) : GenerateOutcome
    data class Rejected(val issues: List<ValidationIssue>, val attempts: Int) : GenerateOutcome

    /** Nothing in the bank is schedulable right now. */
    data object NoWords : GenerateOutcome

    /**
     * Transport or gateway failure. [failure] is the machine code the UI words a reply from;
     * null means "no gateway was reached to blame", in which case [detail] is all there is.
     */
    data class Failed(val failure: ProbeFailure? = null, val detail: String) : GenerateOutcome
}

/**
 * Assembles the word list, calls the model, validates and repairs at most
 * [maxRepairs] times before giving up. Chapters are stored as the model's own
 * JSON so the reader can re-parse them without a lossy round trip.
 */
class ChapterGenerator(
    private val queue: WordQueue,
    private val chapterDao: ChapterDao,
    private val story: StoryContextSource,
    private val wordDao: WordDao,
    private val clientFor: () -> LlmClient,
    private val maxRepairs: Int = 2,
    private val targetHanChars: Int = DEFAULT_TARGET_HAN_CHARS,
) {
    suspend fun generate(
        genre: String,
        density: Density,
        dailyNewLimit: Int,
        now: Instant = Instant.now(),
    ): GenerateOutcome {
        val today = queue.todaysQueue(now, dailyNewLimit)
        if (today.newWords.isEmpty() && today.reviewWords.isEmpty()) {
            return GenerateOutcome.NoWords
        }
        val request = buildRequest(
            genre = genre,
            density = density,
            newWords = today.newWords,
            reviewWords = today.reviewWords,
            chapterNumber = nextChapterNumber(genre),
            excludeChapterId = 0,
        )
        return write(request, replaceId = null)
    }

    /**
     * 重写本章：沿用这一章已经嵌过的词与编号，成功才替换原行，失败则原章原样保留。
     * 词的状态一个都不动——重写只是重写作文，不是重学一遍单词。短篇换用短篇 prompt，
     * 但同样保持"同一批词、同一个腔调"。
     */
    suspend fun rewrite(
        chapterId: Long,
        density: Density,
    ): GenerateOutcome {
        val chapter = chapterDao.byId(chapterId)
            ?: return GenerateOutcome.Failed(detail = "chapter_missing")
        val lookups = ChapterParser.parse(chapter.segmentsJson)
            ?.paragraphs?.flatten()
            ?.filterIsInstance<Segment.Embedded>()
            ?.map { it.word.lookup }
            .orEmpty()
        val words = wordDao.findByWords(lookups.distinct())
        if (words.isEmpty()) return GenerateOutcome.NoWords
        val short = chapter.kind == KIND_SHORT
        val request = if (short) {
            shortRequest(
                ShortPlan.of(chapter.genre, chapter.toneText, chapter.idea),
                words,
                shortCharsFor(words.size),
            )
        } else {
            buildRequest(
                genre = chapter.genre,
                density = density,
                newWords = words.filter { it.isNew },
                reviewWords = words.filterNot { it.isNew },
                chapterNumber = chapter.number,
                excludeChapterId = chapterId,
            )
        }
        return write(
            request = request,
            replaceId = chapterId,
            kind = chapter.kind,
            minHanChars = if (short) MIN_SHORT_HAN_CHARS else MIN_HAN_CHARS,
        )
    }

    /**
     * v1.5 短篇：用今日队列里最多 15 个词写一篇自足的短篇。它不读、也不写故事档案，
     * 所以短篇和连载互不干扰。
     */
    suspend fun generateShort(
        plan: ShortPlan,
        now: Instant = Instant.now(),
        dailyNewLimit: Int = DEFAULT_DAILY_NEW,
    ): GenerateOutcome {
        val today = queue.todaysQueue(now, dailyNewLimit)
        val words = (today.newWords + today.reviewWords).take(SHORT_WORD_CAP)
        if (words.isEmpty()) return GenerateOutcome.NoWords
        return write(
            request = shortRequest(plan, words, shortCharsFor(words.size)),
            replaceId = null,
            kind = KIND_SHORT,
            minHanChars = MIN_SHORT_HAN_CHARS,
        )
    }

    private fun shortRequest(
        plan: ShortPlan,
        words: List<WordEntity>,
        targetHanChars: Int,
    ) = ChapterRequest(
        genre = plan.genreKey,
        chapterNumber = 0,
        targetHanChars = targetHanChars,
        density = Density.SPARSE,
        newWords = words.filter { it.isNew }.map { it.brief() },
        reviewWords = words.filterNot { it.isNew }.map { it.brief() },
        story = StoryContext(),
        shortPlan = plan,
    )

    private fun shortCharsFor(wordCount: Int): Int =
        (wordCount * SHORT_CHARS_PER_WORD).coerceIn(MIN_SHORT_CHARS, MAX_SHORT_CHARS)

    private suspend fun write(
        request: ChapterRequest,
        replaceId: Long?,
        kind: String = KIND_SERIAL,
        minHanChars: Int = MIN_HAN_CHARS,
    ): GenerateOutcome {
        val client = clientFor()
        val task = promptFor(request, kind)
        var previous = ""
        var issues = listOf<ValidationIssue>()

        for (attempt in 0..maxRepairs) {
            val userPrompt = if (attempt == 0) {
                task
            } else {
                PromptBuilder.repair(task, issues, previous)
            }
            val raw = try {
                client.complete(PromptBuilder.system(), userPrompt)
            } catch (e: LlmException) {
                return GenerateOutcome.Failed(e.failure, e.message ?: "request failed")
            }
            previous = raw

            val parsed = ChapterParser.parse(raw)
            issues = ChapterValidator.validate(
                parsed,
                request.allWords.map { it.word },
                request.density,
                minHanChars,
            )
            if (issues.isEmpty() && parsed != null) {
                return save(parsed, raw, request, attempt + 1, replaceId, kind)
            }
            if (issues.isEmpty()) {
                issues = listOf(ValidationIssue("unparseable_json"))
            }
        }
        return GenerateOutcome.Rejected(issues, maxRepairs + 1)
    }

    private fun promptFor(request: ChapterRequest, kind: String): String =
        if (kind == KIND_SHORT) {
            PromptBuilder.shortUser(request, request.shortPlan)
        } else {
            PromptBuilder.user(request)
        }

    private suspend fun nextChapterNumber(genre: String): Int {
        // 档案里的章号只是"曾经写到第几章"的记忆：删掉最后一章也不回退，
        // 否则新章会和旧章撞号，阅读页 ORDER BY number DESC 会停在错误的一行。
        return maxOf(story.lastChapterNumber(genre), chapterDao.latest(genre)?.number ?: 0) + 1
    }

    private suspend fun buildRequest(
        genre: String,
        density: Density,
        newWords: List<WordEntity>,
        reviewWords: List<WordEntity>,
        chapterNumber: Int,
        excludeChapterId: Long,
    ): ChapterRequest {
        val wordCount = newWords.size + reviewWords.size
        val tier = Density.bestFor(wordCount, targetHanChars, density)
        return ChapterRequest(
            genre = genre,
            chapterNumber = chapterNumber,
            targetHanChars = Density.charsFor(wordCount, targetHanChars, tier, MIN_HAN_CHARS),
            density = tier,
            newWords = newWords.map { it.brief() },
            reviewWords = reviewWords.map { it.brief() },
            story = story.contextFor(genre, excludeChapterId),
        )
    }

    private suspend fun save(
        chapter: ParsedChapter,
        rawJson: String,
        request: ChapterRequest,
        attempts: Int,
        replaceId: Long?,
        kind: String,
    ): GenerateOutcome {
        val number = request.chapterNumber
        val stored = ChapterEntity(
            id = replaceId ?: 0,
            number = number,
            title = chapter.title,
            genre = request.genre,
            createdAtSec = Instant.now().epochSecond,
            segmentsJson = ChapterParser.extractJson(rawJson).orEmpty(),
            summary = chapter.summary,
            kind = kind,
            toneText = request.shortPlan.toneText,
            idea = request.shortPlan.idea,
        )
        val id = if (replaceId == null) chapterDao.insert(stored) else {
            chapterDao.update(stored)
            replaceId
        }
        // 登场人物与"本章变化"合并进人物状态表，不需要额外一次调用（docs/adr/0005 第 3 条）。
        // 短篇是自足的，不属于任何一本书，所以不改动连载的上下文。
        if (kind == KIND_SERIAL) {
            story.recordChapterOutcome(request.genre, number, chapter.characters, chapter.changes)
        }
        return GenerateOutcome.Saved(stored.copy(id = id), attempts = attempts)
    }

    private fun WordEntity.brief() = WordBrief(
        id = id,
        word = word,
        gloss = translation.ifBlank { definition },
        state = mastery().toSegmentToken(),
    )

    private companion object {
        const val MIN_HAN_CHARS = 400

        /** AGENTS.md §2：每日新词上限默认 25，调用方没传时按默认值取队列。 */
        const val DEFAULT_DAILY_NEW = 25

        /** 千字左右、每章 20–30 词（AGENTS.md §2）：再密就得靠重复塞词凑数，句子会先垮。 */
        const val DEFAULT_TARGET_HAN_CHARS = 1000

        /** 短篇按"15 词 → 1500–2500 字"给篇幅，词少时不缩到 1500 以下（AGENTS.md §2）。 */
        const val SHORT_WORD_CAP = 15
        const val SHORT_CHARS_PER_WORD = 110
        const val MIN_SHORT_CHARS = 1500
        const val MAX_SHORT_CHARS = 2500

        /** 校验底线比目标低一截：模型写到 1200 字就算可用，不为凑字数再修一轮。 */
        const val MIN_SHORT_HAN_CHARS = 1200
    }
}
