package com.immerlex.core.quiz

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.LOG_KIND_ATTEMPT
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.ReviewHistoryDao
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.TrickyDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
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
 * 把一次作答折算成一条记录（生词第一次只 learn，之后过认出闸门）。不另建排期，也不改 FSRS 公式。
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

    /**
     * 今日到期 + 今天学过但还没回忆的词 + 今天的新词；今天已经碰过一轮的不再重复考，它们走「今日复习」。
     * 新词也进来（v4.4）：没读过课文的人一样可以开测，第一次不管答对答错都只算打过照面。
     */
    suspend fun dueCandidateIds(now: Instant, dailyNewLimit: Int): List<Long> {
        val dayStart = boundary.startOf(now)
        val queue = schedule.todaysQueue(now, dailyNewLimit)
        val learned = logDao.learnedWordIdsSince(dayStart)
        val handled = schedule.handledWordIds(now)
        return (queue.reviewWords.map { it.id } + queue.newWords.map { it.id } + learned.sorted())
            .distinct()
            .filterNot { it in handled }
    }

    /**
     * 今天的总词数：到期词 + 今天的新词（词，不是题）。
     * 这是分段唯一的被除数——今天分几段坐由设置页决定，一段几道由它除出来。
     * 用的是**原始**到期队列，所以答完一轮、词今天不再压人之后，每一天的段长也不会越缩越小。
     */
    suspend fun todayTotal(now: Instant, dailyNewLimit: Int): Int {
        val queue = schedule.todaysQueue(now, dailyNewLimit)
        return (queue.reviewWords + queue.newWords).distinctBy { it.id }.size
    }

    /**
     * 今日复习：今天被测验考过、但今天还没真正回忆成功过的词。
     * 认出来一次（攒满认出闸门升成的那条 recall GOOD）它就从这一格里下去；答错写的是 recall AGAIN，
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
    }

    suspend fun planReview(
        now: Instant,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val candidates = reviewCandidates(now)
        if (candidates.isEmpty()) return QuizSession(QuizMode.REVIEW, emptyList())
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), candidates.size, segments)
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.REVIEW, candidates.take(size), now, seed, onStage, onMaterial)
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
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val ids = dueCandidateIds(now, dailyNewLimit)
        if (ids.isEmpty()) return QuizSession(QuizMode.DUE, emptyList())
        onStage(QuizStage.READING_LOGS)
        val sorted = wordDao.findByIds(ids)
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), sorted.size, segments)
        return build(QuizMode.DUE, sorted.take(size), now, seed, onStage, onMaterial)
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
    }

    suspend fun planTricky(
        now: Instant,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val candidates = trickyCandidates(now)
        if (candidates.isEmpty()) return QuizSession(QuizMode.TRICKY, emptyList())
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), candidates.size, segments)
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.TRICKY, candidates.take(size), now, seed, onStage, onMaterial)
    }

    /**
     * 宿敌：最后一次真记账是「没想起来」的那些词，最近把你说倒的那个排在最前面。
     * 这里不加"今天已经回忆过就不再考"的闸门——认出来一次它自己就从名单上下去了，
     * 还留在名单上的，就是今天也没打赢的那个。
     */
    suspend fun nemesisCandidates(): List<WordEntity> {
        val ids = Nemesis.wordIds(historyDao.recallEvents())
        if (ids.isEmpty()) return emptyList()
        val byId = wordDao.findByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun planNemesis(
        now: Instant,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val candidates = nemesisCandidates()
        if (candidates.isEmpty()) return QuizSession(QuizMode.NEMESIS, emptyList())
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), candidates.size, segments)
        onStage(QuizStage.READING_LOGS)
        return build(QuizMode.NEMESIS, candidates.take(size), now, seed, onStage, onMaterial)
    }

    suspend fun planEarly(
        now: Instant,
        excludeWordIds: Set<Long>,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
        onStage: (QuizStage) -> Unit = {},
        onMaterial: (QuizMaterialPrep) -> Unit = {},
    ): QuizSession {
        onStage(QuizStage.COLLECTING)
        val candidates = earlyCandidates(now)
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), candidates.size, segments)
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
     * 连连看用的词：和「今日测验」同一批（今天还没碰过的），只要两边都有字就能上桌。
     * 段长与其他入口同一个除法：今天的词数 ÷ 设置里的段数。
     */
    private suspend fun matchPairs(now: Instant, dailyNewLimit: Int, segments: Int): List<MatchPair> {
        val ids = dueCandidateIds(now, dailyNewLimit)
        if (ids.isEmpty()) return emptyList()
        val sorted = wordDao.findByIds(ids)
            .filter { it.word.isNotBlank() && it.translation.isNotBlank() }
            .sortedWith(compareBy({ it.dueEpochSec ?: Long.MAX_VALUE }, { it.id }))
        if (sorted.isEmpty()) return emptyList()
        val size = QuizRound.perSegmentOr(todayTotal(now, dailyNewLimit), sorted.size, segments)
        return sorted.take(size).map { MatchPair(it.id, it.word, it.translation) }
    }

    /** 今日页入口上那个数：这一组连连看能摆几对（凑不满 [MatchBoards.MIN_PAIRS] 对就是 0，整格不出现）。 */
    suspend fun matchPairCount(
        now: Instant,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
    ): Int = MatchBoards.boards(matchPairs(now, dailyNewLimit, segments), 0L).sumOf { it.left.size }

    /** 一组连连看：挑词在这里，分屏与打乱都交给 [MatchBoards]。 */
    suspend fun planMatch(
        now: Instant,
        dailyNewLimit: Int,
        segments: Int = QuizRound.DEFAULT_SEGMENTS,
        seed: Long = now.epochSecond,
    ): MatchPlan = MatchPlan(MatchBoards.boards(matchPairs(now, dailyNewLimit, segments), seed))

    /**
     * 连连看连上了一对：只有第一下就连对才写一笔（闸门里和四选一一样值 1 分）。
     * 先连错、缩小范围后才连上的不算——最后一对不用认也连得上，那种"对上了"说明不了记得。
     * 连错本身也不记账：错的是那个配对，不是这两个词里任何一个。
     */
    suspend fun recordMatch(wordId: Long, firstTry: Boolean, now: Instant = Instant.now()): Boolean {
        if (!firstTry) return false
        val word = wordDao.findById(wordId) ?: return false
        val first = schedule.learn(word, Rating.GOOD, LOG_SOURCE_QUIZ, now)
        if (first != null) return true
        return schedule.recognize(
            word = word,
            correct = true,
            source = LOG_SOURCE_QUIZ,
            questionType = MATCH_QUESTION_TYPE,
            now = now,
        ) != null
    }

    /**
     * 一次作答折算成一次"认出来"（[ScheduleService.recognize]）。生词第一次是例外（v4.4）：
     * 没读过课文的人也能开测，第一次作答不管对错都只算打过照面——先走 [ScheduleService.learn]
     * 建立排期（来源记 quiz，所以今天算是碰过了），不算一次回忆，也不往认出闸门里攒分。
     * 从第二次起才进闸门：答对按题型攒分，攒满 [com.immerlex.core.schedule.RecognitionGate.GOOD_POINTS]
     * 分才写一次 recall GOOD；答错立即一次 recall AGAIN 并清零。刚学过的词也能作答，这里不按 due 拦。
     */
    suspend fun record(
        question: QuizQuestion,
        correct: Boolean,
        now: Instant = Instant.now(),
    ): Boolean {
        val word = wordDao.findById(question.wordId) ?: return false
        val first = schedule.learn(
            word = word,
            rating = if (correct) Rating.GOOD else Rating.AGAIN,
            source = LOG_SOURCE_QUIZ,
            now = now,
        )
        if (first != null) return true
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
     * 一个词标成易错后答对攒满 [TrickyRetirement.RETIRE_POINTS] 分（认出来与真回忆都算，
     * 每次值几分跟认出闸门同一把尺），就报出它的词面，问一句要不要移出易错；
     * 没标过、或中间答错过，返回 null。
     */
    suspend fun retirementCandidate(wordId: Long): String? {
        val word = wordDao.findById(wordId) ?: return null
        val name = word.word.trim().lowercase()
        val marked = trickyDao.byWord(name) ?: return null
        val points = TrickyRetirement.correctPointsSinceMarked(logDao.forWord(wordId), marked.createdAtSec)
        return if (TrickyRetirement.shouldRetire(points)) name else null
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
            // 认出闸门挡下的 attempt 也算一次被考：同一天里再考同一个词，选项该换一叠。
            attempts = logsByWord.mapValues { (_, logs) ->
                logs.count { it.kind == LOG_KIND_RECALL || it.kind == LOG_KIND_ATTEMPT }
            },
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
        // 易错表里的释义常常是阅读页那一刻的简短说法；词库里有这一行就以词库为准，
        // 免得同一个词当正确答案与当干扰项时摆出两种写法。
        trickyDao.byWords(names).forEach { put(glosses, it.word, it.gloss) }
        wordDao.findByWords(names).forEach { put(glosses, it.word, it.translation) }
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
        /** 提前复习先扫这么多个，再剔掉今天已经回忆过的；一天分几段由设置页定，段长由今天的词数除出来。 */
        private const val EARLY_SCAN = 200
        private const val POOL_SIZE = 200
        private const val CHAPTER_SCAN = 20
        /** 易混词只留前面几个：选项一共三个，全被固定项占住就永远换不了样。 */
        private const val MAX_CONFUSABLES = 4
    }
}
