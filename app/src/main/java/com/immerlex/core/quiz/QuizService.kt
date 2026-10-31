package com.immerlex.core.quiz

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.LOG_KIND_RECALL
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
 * [material] 存在时每个词只向模型要一回，之后都读库里的这一行。
 */
class QuizService(
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val chapterDao: ChapterDao,
    private val trickyDao: TrickyDao,
    private val schedule: ScheduleService,
    private val enricher: QuizEnricher? = null,
    private val material: QuizMaterialStore? = null,
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

    suspend fun planDue(
        now: Instant,
        dailyNewLimit: Int,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val ids = dueCandidateIds(now, dailyNewLimit)
        if (ids.isEmpty()) return QuizSession(QuizMode.DUE, emptyList())
        onStage(QuizStage.READING_LOGS)
        val words = wordDao.findByIds(ids)
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
            .take(SESSION_LIMIT)
        return build(QuizMode.DUE, words, now, seed, onStage)
    }

    suspend fun planEarly(
        now: Instant,
        excludeWordIds: Set<Long>,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val candidates = earlyCandidates(now)
        val fresh = candidates.filterNot { it.id in excludeWordIds }
        val picked = if (fresh.size >= EARLY_GROUP_SIZE) {
            fresh.take(EARLY_GROUP_SIZE)
        } else {
            fresh + candidates.filter { it.id in excludeWordIds }.take(EARLY_GROUP_SIZE - fresh.size)
        }
        if (picked.isEmpty()) return QuizSession(QuizMode.EARLY, emptyList())
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.EARLY, picked, now, seed, onStage)
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
        onStage: (QuizStage) -> Unit,
    ): QuizSession {
        val logsByWord = logDao.forWords(words.map { it.id }).groupBy { it.wordId }
        val enrichment = enrichmentFor(words, now, onStage)
        onStage(QuizStage.BUILDING)
        val names = words.map { it.word.trim().lowercase() }
        val tricky = trickyDao.byWords(names).mapTo(HashSet()) { it.word }
        val context = QuestionContext(
            pool = (wordDao.randomWords(POOL_SIZE) + words).distinctBy { it.id },
            sentences = ClozeSentences.fromChapters(chapterDao.recentSegments(CHAPTER_SCAN)),
            enrichment = enrichment,
            confusables = confusablesFor(words),
            attempts = logsByWord.mapValues { (_, logs) -> logs.count { it.kind == LOG_KIND_RECALL } },
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

    /**
     * 素材先读库里已经存下的那一行，只把从没问过的词交给模型——慢只该慢第一次，
     * 不该每次测验都慢一遍。没配 key 或请求失败时不落库，下次还会再要一回。
     */
    private suspend fun enrichmentFor(
        words: List<WordEntity>,
        now: Instant,
        onStage: (QuizStage) -> Unit,
    ): QuizEnrichment {
        val stored = material?.load(words).orEmpty()
        val pending = words.filterNot { it.id in stored }
        if (pending.isEmpty()) return QuizEnrichment(stored)
        if (enricher == null) return QuizEnrichment(stored)
        onStage(QuizStage.ASKING_AI)
        val fresh = enricher.enrich(pending)
        material?.save(fresh, now.epochSecond)
        return QuizEnrichment(stored + fresh.entries)
    }

    /**
     * 用户亲手标过的易混词排在所有候选之前：affect ↔ effect 是这个人真的会错的那一组，
     * 模型给的池子再大也换不掉它。释义取易错词表，其次取词库，两处都没有就不做成选项。
     */
    private suspend fun confusablesFor(words: List<WordEntity>): Map<Long, List<EnrichedDistractor>> {
        val names = words.map { it.word.trim().lowercase() }
        val links = trickyDao.linksAmong(names)
        if (links.isEmpty()) return emptyMap()
        val glosses = glossFor((links.map { it.wordA } + links.map { it.wordB }).distinct())
        val out = mutableMapOf<Long, List<EnrichedDistractor>>()
        words.forEach { word ->
            val name = word.word.trim().lowercase()
            val partners = links
                .filter { it.wordA == name || it.wordB == name }
                .map { if (it.wordA == name) it.wordB else it.wordA }
                .distinct()
                .mapNotNull { other -> glosses[other]?.let { EnrichedDistractor(other, it) } }
                .take(MAX_CONFUSABLES)
            if (partners.isNotEmpty()) out[word.id] = partners
        }
        return out
    }

    private suspend fun glossFor(names: List<String>): Map<String, String> {
        val glosses = mutableMapOf<String, String>()
        wordDao.findByWords(names).forEach { put(glosses, it.word, it.translation) }
        trickyDao.byWords(names).forEach { put(glosses, it.word, it.gloss) }
        return glosses
    }

    private fun put(into: MutableMap<String, String>, word: String, gloss: String) {
        val text = gloss.trim()
        if (text.isNotEmpty()) into[word.trim().lowercase()] = text
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
        /** 一组最多 20 题：48 个到期词一次全塞进去，AI 慢的时候用户会以为卡死。 */
        const val SESSION_LIMIT = 20
        const val EARLY_GROUP_SIZE = 5
        const val SOURCE_QUIZ = "quiz"
        private const val POOL_SIZE = 200
        private const val CHAPTER_SCAN = 20
        /** 易混词只留前面几个：选项一共三个，全被固定项占住就永远换不了样。 */
        private const val MAX_CONFUSABLES = 4
    }
}
