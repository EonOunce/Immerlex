package com.immerlex.core.quiz

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.TrickyDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.schedule.ScheduleService
import java.time.Instant
import java.time.ZoneId

/**
 * 独立测验的编排层。只做三件事：挑出今天该测的词、按题型策略出题、
 * 把一次作答折算成一次 [ScheduleService.recall]。不另建排期，也不改 FSRS 公式。
 * 本地题库优先，[enricher] 只补缺失素材，失败就静默退化。
 */
class QuizService(
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val chapterDao: ChapterDao,
    private val trickyDao: TrickyDao,
    private val schedule: ScheduleService,
    private val enricher: QuizEnricher? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val builders: List<QuestionBuilder> = DEFAULT_QUESTION_BUILDERS,
) {

    /** 今日到期 + 今天学过但还没回忆的词；今天已经回忆过的不再重复考。 */
    suspend fun dueCandidateIds(now: Instant, dailyNewLimit: Int): List<Long> {
        val dayStart = startOfDay(now)
        val queue = schedule.todaysQueue(now, dailyNewLimit)
        val learned = logDao.learnedWordIdsSince(dayStart)
        val recalled = logDao.recalledWordIdsSince(dayStart).toSet()
        return (queue.reviewWords.map { it.id } + learned.sorted())
            .distinct()
            .filterNot { it in recalled }
    }

    /** 今天碰过、但还没到期、最不稳定的几个词；提前复习只从这些词里取。 */
    suspend fun earlyCandidates(now: Instant): List<WordEntity> =
        wordDao.findByIds(logDao.touchedWordIdsSince(startOfDay(now)))
            .filter { it.stability != null && it.dueEpochSec != null && it.dueEpochSec > now.epochSecond }
            .sortedWith(compareBy({ it.stability ?: 0.0 }, { it.lastReviewEpochSec ?: 0L }, { it.id }))

    suspend fun planDue(now: Instant, dailyNewLimit: Int, seed: Long = now.epochSecond): QuizSession {
        val ids = dueCandidateIds(now, dailyNewLimit)
        if (ids.isEmpty()) return QuizSession(QuizMode.DUE, emptyList())
        val words = wordDao.findByIds(ids)
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
        return build(QuizMode.DUE, words, now, seed)
    }

    suspend fun planEarly(now: Instant, excludeWordIds: Set<Long>, seed: Long = now.epochSecond): QuizSession {
        val candidates = earlyCandidates(now)
        val fresh = candidates.filterNot { it.id in excludeWordIds }
        val picked = if (fresh.size >= EARLY_GROUP_SIZE) {
            fresh.take(EARLY_GROUP_SIZE)
        } else {
            fresh + candidates.filter { it.id in excludeWordIds }.take(EARLY_GROUP_SIZE - fresh.size)
        }
        if (picked.isEmpty()) return QuizSession(QuizMode.EARLY, emptyList())
        return build(QuizMode.EARLY, picked, now, seed)
    }

    /** 一次作答 = 一次独立回忆；答对记记得，答错记忘了。提前复习照常记，不压成困难。 */
    suspend fun record(
        question: QuizQuestion,
        correct: Boolean,
        now: Instant = Instant.now(),
    ): Boolean {
        val word = wordDao.findById(question.wordId) ?: return false
        val result = schedule.recall(
            word = word,
            rating = if (correct) Rating.GOOD else Rating.AGAIN,
            source = SOURCE_QUIZ,
            questionType = question.type.key,
            now = now,
            // 今天的词（含刚学过的）都允许作答；是否该考由候选集决定，不靠 due 再拦一次。
            allowEarly = true,
        )
        return result != null
    }

    private suspend fun build(
        mode: QuizMode,
        words: List<WordEntity>,
        now: Instant,
        seed: Long,
    ): QuizSession {
        val logsByWord = logDao.forWords(words.map { it.id }).groupBy { it.wordId }
        val tricky = trickyDao.byWords(words.map { it.word.trim().lowercase() })
            .mapTo(HashSet()) { it.word }
        val enrichment = enricher?.enrich(words) ?: QuizEnrichment()
        val context = QuestionContext(
            pool = (wordDao.randomWords(POOL_SIZE) + words).distinctBy { it.id },
            sentences = ClozeSentences.fromChapters(chapterDao.recentSegments(CHAPTER_SCAN)),
            enrichment = enrichment,
            seed = seed,
        )
        val questions = mutableListOf<QuizQuestion>()
        val alternatives = mutableMapOf<Long, QuizQuestion>()
        words.forEach { word ->
            val logs = logsByWord[word.id].orEmpty()
            val ordered = QuizPolicy.orderedTypes(
                wordId = word.id,
                logs = logs,
                tricky = word.word.trim().lowercase() in tricky,
                seed = seed,
            )
            val primary = firstBuildable(word, context, ordered) ?: return@forEach
            questions += primary
            alternativeFor(word, context, primary.type)?.let { alternatives[word.id] = it }
        }
        return QuizSession(mode, questions, alternatives)
    }

    private fun firstBuildable(
        word: WordEntity,
        context: QuestionContext,
        types: List<QuizType>,
    ): QuizQuestion? {
        types.forEach { type ->
            builderOf(type)?.build(word, context)?.let { return it }
        }
        return null
    }

    /** 答错后的重练优先换更简单的题型，且一定不同于刚才考的那一种。 */
    private fun alternativeFor(
        word: WordEntity,
        context: QuestionContext,
        justAsked: QuizType,
    ): QuizQuestion? {
        QuizType.entries.filter { it != justAsked }.forEach { type ->
            builderOf(type)?.build(word, context)?.let { return it }
        }
        return null
    }

    private fun builderOf(type: QuizType): QuestionBuilder? = builders.firstOrNull { it.type == type }

    private fun startOfDay(now: Instant): Long =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    companion object {
        const val EARLY_GROUP_SIZE = 5
        const val SOURCE_QUIZ = "quiz"
        private const val POOL_SIZE = 200
        private const val CHAPTER_SCAN = 20
    }
}
