package com.immerlex.core.schedule

import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.db.RecallSnapshotDao
import com.immerlex.core.db.RecallSnapshotEntity
import com.immerlex.core.db.ReviewHistoryDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.fsrs.Scheduler
import java.time.Instant
import kotlin.math.roundToInt

/**
 * 战绩页的一份只读快照（docs/adr/0030 记录口径）。都是"给人看的数"，不参与排期。
 *
 * 卡片当下算出来的：[canRecall] = 已学词的 [Scheduler.retrievability] 之和四舍五入，
 * 读作"此刻大约能想起几个词"；[mastered] = 间隔 ≥ [ScheduleService.MASTERED_INTERVAL_DAYS] 天的词数；
 * [mostFailedWord] 是忘得最多次的那一个。
 *
 * 历史算出来的：[rank] 按已掌握数落在哪一档；[peak] 把整段记录重放一遍得到的巅峰分；
 * [duel] 把这同一条曲线按本地周折成"这周最好 vs 上周最好"；
 * [bestDay] 是以前最好的一天有过多少个（今天的数字就在 [canRecall] 里，跟自己比不用两个数）；
 * [rivals] 是最后一次真记账答错的那些词——它们欠一次复仇。
 */
data class RetentionReport(
    val totalWords: Int,
    val learnedWords: Int,
    val canRecall: Int,
    val mastered: Int,
    val mostFailedWord: String?,
    val mostFailedGloss: String?,
    val mostFailedLapses: Int,
    val rank: Rank,
    val peak: PeakScore,
    val duel: WeekDuel,
    val bestDay: Int,
    val rivals: List<RivalWord>,
) {
    /** 还没学过一个词时的样子；战绩页在数据到手前先画这一份。 */
    companion object {
        fun blank() = RetentionReport(
            totalWords = 0,
            learnedWords = 0,
            canRecall = 0,
            mastered = 0,
            mostFailedWord = null,
            mostFailedGloss = null,
            mostFailedLapses = 0,
            rank = RankLadder.of(0),
            peak = PeakScoreReplay.replay(emptyList()),
            duel = WeekDuel(null, null),
            bestDay = 0,
            rivals = emptyList(),
        )
    }
}

/** 一个还欠着复仇的词。 */
data class RivalWord(val id: Long, val word: String, val gloss: String)

/**
 * 战绩页的唯一数据源。只读词表与复习历史，**绝不改卡片、不改排期、不写复习记录**；
 * 唯一往库里落笔的是"今天这个数字"的影子——那是 [RetentionReport.bestDay] 的对照，
 * 因为回忆把握算的是卡片此刻的状态，明天再也回推不出今天的样子。
 */
class StatsService(
    private val wordDao: WordDao,
    private val historyDao: ReviewHistoryDao,
    private val snapshotDao: RecallSnapshotDao,
    private val scheduler: Scheduler,
    private val boundary: DayBoundary,
) {

    suspend fun report(now: Instant): RetentionReport {
        val words = wordDao.allWords()
        val learned = words.filter { !it.isNew }
        val events = historyDao.recallEvents()
        val today = boundary.dayOf(now).toEpochDay()

        if (learned.isEmpty()) {
            return RetentionReport.blank().copy(
                totalWords = words.size,
                peak = PeakScoreReplay.replay(events),
                duel = duelOf(events, now),
                bestDay = snapshotDao.bestBefore(today),
            )
        }

        var recallSum = 0.0
        var mastered = 0
        for (w in learned) {
            recallSum += scheduler.retrievability(w.toCard(now), now)
            if (w.intervalDays >= ScheduleService.MASTERED_INTERVAL_DAYS) mastered++
        }
        val mostFailed = learned.maxByOrNull { it.lapses }?.takeIf { it.lapses > 0 }
        val canRecall = recallSum.roundToInt()
        val peak = PeakScoreReplay.replay(events)
        val duel = duelOf(events, now)
        val rivalIds = Nemesis.wordIds(events)
        val byId = learned.associateBy { it.id }

        snapshotToday(today, now, canRecall, mastered)

        return RetentionReport(
            totalWords = words.size,
            learnedWords = learned.size,
            canRecall = canRecall,
            mastered = mastered,
            mostFailedWord = mostFailed?.word,
            mostFailedGloss = mostFailed?.translation?.ifBlank { null },
            mostFailedLapses = mostFailed?.lapses ?: 0,
            rank = RankLadder.of(mastered),
            peak = peak,
            duel = duel,
            bestDay = snapshotDao.bestBefore(today),
            rivals = rivalIds.mapNotNull { id ->
                byId[id]?.let { RivalWord(id, it.word, it.translation) }
            },
        )
    }

    /**
     * 影子对决：把 [peak] 那条曲线按本地周折起来，这周的最高对上上周的最高。
     * 周界跟着 [DayBoundary] 的时区走，与"哪天算今天"是同一把尺。
     */
    private suspend fun duelOf(events: List<RecallEventRow>, now: Instant): WeekDuel {
        val thisWeek = WeeklyDuel.weekOf(boundary.dayOf(now).toEpochDay())
        val weeks = events.map { WeeklyDuel.weekOf(boundary.dayOf(it.epochSec)) }
        return WeeklyDuel.of(events, weeks, thisWeek)
    }

    /** 只往"更高"的方向写，所以同一天里反复打开战绩页不会把高点抹平。 */
    private suspend fun snapshotToday(today: Long, now: Instant, canRecall: Int, mastered: Int) {
        val row = RecallSnapshotEntity(today, canRecall, mastered, now.epochSecond)
        snapshotDao.insertIfAbsent(row)
        snapshotDao.raiseBest(today, canRecall, mastered, now.epochSecond)
    }
}
