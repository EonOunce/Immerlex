package com.immerlex.core.ai

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.StoryBibleDao
import com.immerlex.core.db.StoryBibleEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.schedule.WordQueue
import com.immerlex.core.word.mastery
import com.immerlex.core.word.toSegmentToken
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface GenerateOutcome {
    data class Saved(val chapter: ChapterEntity, val attempts: Int) : GenerateOutcome
    data class Rejected(val issues: List<ValidationIssue>, val attempts: Int) : GenerateOutcome

    /** Nothing in the bank is schedulable right now. */
    data object NoWords : GenerateOutcome

    /** Transport or gateway failure; [detail] is technical text safe to show in a debug toast. */
    data class Failed(val detail: String) : GenerateOutcome
}

/**
 * Assembles the word list, calls the model, validates, and repairs at most
 * [maxRepairs] times before giving up. Chapters are stored as the model's own
 * JSON so the reader can re-parse them without a lossy round trip.
 */
class ChapterGenerator(
    private val queue: WordQueue,
    private val chapterDao: ChapterDao,
    private val bibleDao: StoryBibleDao,
    private val clientFor: () -> LlmClient,
    private val maxRepairs: Int = 2,
    private val targetHanChars: Int = 900,
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

        val request = buildRequest(genre, density, today.newWords, today.reviewWords, now)
        val client = clientFor()
        var previous = ""
        var issues = listOf<ValidationIssue>()

        for (attempt in 0..maxRepairs) {
            val userPrompt = if (attempt == 0) {
                PromptBuilder.user(request)
            } else {
                PromptBuilder.repair(PromptBuilder.user(request), issues, previous)
            }
            val raw = try {
                client.complete(PromptBuilder.system(), userPrompt)
            } catch (e: LlmException) {
                return GenerateOutcome.Failed(e.message ?: "request failed")
            }
            previous = raw

            val parsed = ChapterParser.parse(raw)
            issues = ChapterValidator.validate(parsed, request.allWords.map { it.word }, density, MIN_HAN_CHARS)
            if (issues.isEmpty() && parsed != null) {
                return save(parsed, raw, request, attempt + 1)
            }
            if (issues.isEmpty()) {
                issues = listOf(ValidationIssue("unparseable_json"))
            }
        }
        return GenerateOutcome.Rejected(issues, maxRepairs + 1)
    }

    private suspend fun buildRequest(
        genre: String,
        density: Density,
        newWords: List<WordEntity>,
        reviewWords: List<WordEntity>,
        now: Instant,
    ): ChapterRequest {
        val bible = bibleDao.forGenre(genre)
        return ChapterRequest(
            genre = genre,
            chapterNumber = (bible?.lastChapterNumber ?: 0) + 1,
            targetHanChars = targetHanChars,
            density = density,
            newWords = newWords.map { it.brief() },
            reviewWords = reviewWords.map { it.brief() },
            recaps = chapterDao.recentRecaps(genre, RECAP_CHAPTERS).reversed()
                .map { RecapLine(it.number, it.title, it.summary) },
            characters = parseCharacters(bible?.charactersJson.orEmpty()),
        )
    }

    private suspend fun save(
        chapter: ParsedChapter,
        rawJson: String,
        request: ChapterRequest,
        attempts: Int,
    ): GenerateOutcome {
        val number = request.chapterNumber
        val stored = ChapterEntity(
            number = number,
            title = chapter.title,
            genre = request.genre,
            createdAtSec = Instant.now().epochSecond,
            segmentsJson = ChapterParser.extractJson(rawJson).orEmpty(),
            summary = chapter.summary,
        )
        val id = chapterDao.insert(stored)
        val existing = bibleDao.forGenre(request.genre)
        val cast = (chapter.characters + request.characters).distinct().take(MAX_CHARACTERS)
        bibleDao.upsert(
            StoryBibleEntity(
                id = existing?.id ?: 0,
                genre = request.genre,
                charactersJson = encodeCharacters(cast),
                synopsis = chapter.summary.ifBlank { existing?.synopsis.orEmpty() },
                lastChapterNumber = number,
                updatedAtSec = Instant.now().epochSecond,
            ),
        )
        return GenerateOutcome.Saved(stored.copy(id = id), attempts = attempts)
    }

    private fun WordEntity.brief() = WordBrief(
        id = id,
        word = word,
        gloss = translation.ifBlank { definition },
        state = mastery().toSegmentToken(),
    )

    private fun parseCharacters(array: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(array)
    }.getOrDefault(emptyList())

    private fun encodeCharacters(values: List<String>): String = json.encodeToString(values)

    private companion object {
        const val MIN_HAN_CHARS = 400

        /** How many earlier chapter summaries travel with the prompt. */
        const val RECAP_CHAPTERS = 3

        /** Newest first: once the cap is hit, stale characters age out instead of the list freezing. */
        const val MAX_CHARACTERS = 12

        val json = Json { explicitNulls = false }
    }
}
