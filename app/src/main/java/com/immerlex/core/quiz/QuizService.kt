package com.immerlex.core.quiz

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.ReviewHistoryDao
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.TrickyDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.schedule.DayBoundary
import com.immerlex.core.schedule.Nemesis
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.tricky.TrickyRetirement
import com.immerlex.core.word.wordSurface
import java.time.Instant

/**
 * 出题前的素材账：这一组里几个词库里已经有素材、几个还得问一次模型。
 * 界面只拿它说一句话，记账与出题都不受影响。
 */
data class QuizMaterialPrep(val ready: Int, val toAsk: Int, val canAsk: Boolean)

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
    /** 宿敌名单从真记账的历史里现推，不另建表。 */
    private val historyDao: ReviewHistoryDao,
    private val schedule: ScheduleService,
    /** "今天"的起点：凌晨那段可能还算前一天，与今日页、阅读页同一套判定。 */
    private val boundary: DayBoundary,
    private val enricher: QuizEnricher? = null,
    private val material: QuizMaterialStore? = null,
    private val builders: List<QuestionBuilder> = DEFAULT_QUESTION_BUILDERS,
) {

    /** 今日到期 + 今天学过但还没回忆的词；今天已经碰过一轮的不再重复考，它们走「今日复习」。 */
    suspend fun dueCandidateIds(now: Instant, dailyNewLimit: Int): List<Long> {
        val dayStart = boundary.startOf(now)
        val queue = schedule.todaysQueue(now, dailyNewLimit)
        val learned = logDao.learnedWordIdsSince(dayStart)
        val handled = schedule.handledWordIds(now)
        return (queue.reviewWords.map { it.id } + learned.sorted())
            .distinct()
            .filterNot { it in handled }
    }

    /**
     * 今日复习：今天被测验考过、但今天还没真正回忆成功过的词。
     * 认出来一次（连着三次升成的那条 recall GOOD）它就从这一格里下去；答错写的是 recall AGAIN，
     * 那是"没认出来"的证据而不是"今天已经算了"，所以照样留着接着练。
     * 这一格不看卡片到没到期——测验里答对只攒 attempt、不动卡片，到期日还停在今天。
     */
    suspend fun reviewCandidates(now: Instant): List<WordEntity> {
        val dayStart = boundary.startOf(now)
        val quizzed = logDao.quizzedWordIdsSince(LOG_SOURCE_QUIZ, dayStart).toSet()
        if (quizzed.isEmpty()) return emptyList()
        val settled = logDao.recalledGoodIdsSince(dayStart)
        val ids = (quizzed - settled.toSet()).toList()
        if (ids.isEmpty()) return emptyList()
        return wordDao.findByIds(ids)
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
            .take(SESSION_LIMIT)
    }

    suspend fun planReview(
        now: Instant,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val words = reviewCandidates(now)
        if (words.isEmpty()) return QuizSession(QuizMode.REVIEW, emptyList())
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.REVIEW, words, now, seed, onStage, onMaterial)
    }

    /**
     * 学过、还没到期的词，最近的到期日排在前面；今天已经回忆过的不再重复考。
     * 不要求"今天碰过"：提前复习本来就是想早一点练练学过的词。
     */
    suspend fun earlyCandidates(now: Instant): List<WordEntity> {
        val recalledToday = logDao.recalledWordIdsSince(boundary.startOf(now)).toSet()
        return wordDao.learnedNotDue(now.epochSecond, EARLY_SCAN)
            .filterNot { it.id in recalledToday }
    }

    suspend fun planDue(
        now: Instant,
        dailyNewLimit: Int,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val ids = dueCandidateIds(now, dailyNewLimit)
        if (ids.isEmpty()) return QuizSession(QuizMode.DUE, emptyList())
        onStage(QuizStage.READING_LOGS)
        val words = wordDao.findByIds(ids)
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
            .take(SESSION_LIMIT)
        return build(QuizMode.DUE, words, now, seed, onStage, onMaterial)
    }

    /**
     * 标过易错、又在词库里的词：错得最多的排前面，其次按到期日。
     * 今天已经回忆过的不再重复考——这条闸门和到期、提前复习用的是同一个口径。
     * 易错表存的是词面，词库里的词可能大写开头，两边都过 [wordSurface] 才对得上。
     */
    suspend fun trickyCandidates(now: Instant): List<WordEntity> {
        val marked = trickyDao.all().mapTo(HashSet()) { wordSurface(it.word) }
        if (marked.isEmpty()) return emptyList()
        val recalledToday = logDao.recalledWordIdsSince(boundary.startOf(now)).toSet()
        return wordDao.allWords()
            .filter { wordSurface(it.word) in marked && it.id !in recalledToday }
            .sortedWith(
                compareByDescending<WordEntity> { it.lapses }
                    .thenBy { it.dueEpochSec ?: Long.MAX_VALUE }
                    .thenBy { it.id },
            )
            .take(SESSION_LIMIT)
    }

    suspend fun planTricky(
        now: Instant,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val words = trickyCandidates(now)
        if (words.isEmpty()) return QuizSession(QuizMode.TRICKY, emptyList())
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.TRICKY, words, now, seed, onStage, onMaterial)
    }

    /**
     * 宿敌：最后一次真记账是「没想起来」的那些词，最近把你说倒的那个排在最前面。
     * 这里不加"今天已经回忆过就不再考"的闸门——认出来一次它自己就从名单上下去了，
     * 还留在名单上的，就是今天也没打赢的那个。
     */
    suspend fun nemesisCandidates(): List<WordEntity> {
        val ids = Nemesis.wordIds(historyDao.recallEvents()).take(SESSION_LIMIT)
        if (ids.isEmpty()) return emptyList()
        val byId = wordDao.findByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun planNemesis(
        now: Instant,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val words = nemesisCandidates()
        if (words.isEmpty()) return QuizSession(QuizMode.NEMESIS, emptyList())
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.NEMESIS, words, now, seed, onStage, onMaterial)
    }

    suspend fun planEarly(
        now: Instant,
        excludeWordIds: Set<Long>,
        groupSize: Int = EarlyReview.DEFAULT_GROUP_SIZE,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val size = EarlyReview.saneOr(groupSize)
        val candidates = earlyCandidates(now)
        val fresh = candidates.filterNot { it.id in excludeWordIds }
        val picked = if (fresh.size >= size) {
            fresh.take(size)
        } else {
            fresh + candidates.filter { it.id in excludeWordIds }.take(size - fresh.size)
        }
        if (picked.isEmpty()) return QuizSession(QuizMode.EARLY, emptyList())
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.EARLY, picked, now, seed, onStage, onMaterial)
    }

    /**
     * 一次作答折算成一次"认出来"（[ScheduleService.recognize]）：四选一认出比凭空想起容易，
     * 答对先攒连对、满 [ScheduleService.GOOD_STREAK] 次才写一次 recall GOOD；答错立即一次
     * recall AGAIN 并清零。是否该考由候选集决定，这里不再按 due 拦一次，所以刚学过的词也能作答。
     */
    suspend fun record(
        question: QuizQuestion,
        correct: Boolean,
        now: Instant = Instant.now(),
    ): Boolean {
        val word = wordDao.findById(question.wordId) ?: return false
        val result = schedule.recognize(
            word = word,
            correct = correct,
            source = LOG_SOURCE_QUIZ,
            questionType = question.type.key,
            now = now,
        )
        return result != null
    }

    /**
     * 一个词标成易错后连对满 [TrickyRetirement.STREAK] 次（认出来与真回忆都算一次答对），
     * 就报出它的词面，问一句要不要移出易错；没标过、或中间答错过，返回 null。
     */
    suspend fun retirementCandidate(wordId: Long): String? {
        val word = wordDao.findById(wordId) ?: return null
        val name = word.word.trim().lowercase()
        val marked = trickyDao.byWord(name) ?: return null
        val streak = TrickyRetirement.correctStreakSinceMarked(logDao.forWord(wordId), marked.createdAtSec)
        return if (TrickyRetirement.shouldRetire(streak)) name else null
    }

    private suspend fun build(
        mode: QuizMode,
        words: List<WordEntity>,
        now: Instant,
        seed: Long,
        onStage: (QuizStage) -> Unit,
        onMaterial: (QuizMaterialPrep) -> Unit,
    ): QuizSession {
        val logsByWord = logDao.forWords(words.map { it.id }).groupBy { it.wordId }
        val pool = (wordDao.randomWords(POOL_SIZE) + words).distinctBy { it.id }
        // 素材整批读一次：本组词的用来出题，池子里的只当搭配题的干扰项，不拿去问 AI。
        val stored = material?.load(pool).orEmpty()
        val sessionIds = words.mapTo(HashSet()) { it.id }
        val enrichment = enrichmentFor(words, stored.filterKeys { it in sessionIds }, now, onStage, onMaterial)
        onStage(QuizStage.BUILDING)
        val names = words.map { it.word.trim().lowercase() }
        val tricky = trickyDao.byWords(names).mapTo(HashSet()) { it.word }
        val trickyIds = words.filter { it.word.trim().lowercase() in tricky }.mapTo(HashSet()) { it.id }
        val context = QuestionContext(
            pool = pool,
            sentences = ClozeSentences.fromChapters(chapterDao.recentSegments(CHAPTER_SCAN)),
            enrichment = enrichment,
            confusables = confusablesFor(words),
            attempts = logsByWord.mapValues { (_, logs) -> logs.count { it.kind == LOG_KIND_RECALL } },
            collocationPool = (stored.values + enrichment.entries.values)
                .flatMap { it.collocations }
                .distinctBy { it.word.trim().lowercase() },
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
        return QuizSession(mode, questions, alternatives, trickyIds)
    }

    /**
     * 素材先读库里已经存下的那一行，只把从没问过的词交给模型——慢只该慢第一次，
     * 不该每次测验都慢一遍。没配 key 或请求失败时不落库，下次还会再要一回。
     */
    private suspend fun enrichmentFor(
        words: List<WordEntity>,
        stored: Map<Long, EnrichedWord>,
        now: Instant,
        onStage: (QuizStage) -> Unit,
        onMaterial: (QuizMaterialPrep) -> Unit,
    ): QuizEnrichment {
        val pending = words.filterNot { it.id in stored }
        // 先报账再动手：用户知道这一组的素材有几个已经在库里、几个要花时间等模型。
        onMaterial(
            QuizMaterialPrep(
                ready = words.size - pending.size,
                toAsk = pending.size,
                canAsk = enricher?.available() == true,
            ),
        )
        if (pending.isEmpty()) return QuizEnrichment(stored)
        if (enricher == null) return QuizEnrichment(stored)
        onStage(QuizStage.ASKING_AI)
        val fresh = enricher.enrich(pending)
        material?.save(fresh, pending, now.epochSecond)
        return QuizEnrichment(stored + fresh.entries)
    }

    /**
     * 用户亲手标过的易混词排在所有候选之前：affect ↔ effect 是这个人真的会错的那一组，
     * 模型给的池子再大也换不掉它。直接挂的词先上，同一连通组件里隔了一层的补足到上限——
     * 只挑直接邻居时常常凑不满三个易混选项，而组件本身就是"这人认定会混的一簇"。
     * 释义取易错词表，其次取词库，两处都没有就不做成选项。
     */
    private suspend fun confusablesFor(words: List<WordEntity>): Map<Long, List<EnrichedDistractor>> {
        val names = words.map { it.word.trim().lowercase() }
        if (trickyDao.linksAmong(names).isEmpty()) return emptyMap()
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        trickyDao.allLinks().forEach { link ->
            adjacency.getOrPut(link.wordA) { mutableSetOf() } += link.wordB
            adjacency.getOrPut(link.wordB) { mutableSetOf() } += link.wordA
        }
        val planned = words.map { word ->
            val name = word.word.trim().lowercase()
            val direct = adjacency[name].orEmpty()
            val component = componentOf(name, adjacency)
            word to (direct.sorted() + (component - direct).sorted())
                .filter { it != name }
                .distinct()
                .take(MAX_CONFUSABLES)
        }
        val glosses = glossFor(planned.flatMap { it.second }.distinct())
        val out = mutableMapOf<Long, List<EnrichedDistractor>>()
        planned.forEach { (word, partners) ->
            val built = partners.mapNotNull { other -> glosses[other]?.let { EnrichedDistractor(other, it) } }
            if (built.isNotEmpty()) out[word.id] = built
        }
        return out
    }

    /** 顺着易混链能走到的所有词（不含起点自己）：直接挂的与隔了一层的都算同一组件。 */
    private fun componentOf(start: String, adjacency: Map<String, Set<String>>): Set<String> {
        val seen = mutableSetOf(start)
        val queue = ArrayDeque<String>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            adjacency[current].orEmpty().forEach { next ->
                if (seen.add(next)) queue.addLast(next)
            }
        }
        seen.remove(start)
        return seen
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

    companion object {
        /** 一组最多 40 题：48 个到期词拆两组就够，既少发一次请求，也不至于让一组长得看不完。 */
        const val SESSION_LIMIT = 40
        /** 提前复习先扫这么多个，再剔掉今天已经回忆过的；三档最大 20，留出十倍的余量。 */
        private const val EARLY_SCAN = 200
        private const val POOL_SIZE = 200
        private const val CHAPTER_SCAN = 20
        /** 易混词只留前面几个：选项一共三个，全被固定项占住就永远换不了样。 */
        private const val MAX_CONFUSABLES = 4
    }
}
