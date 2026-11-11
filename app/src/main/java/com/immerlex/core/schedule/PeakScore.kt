package com.immerlex.core.schedule

import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 巅峰分：把整段复习记录按时间重放一遍，当成一局一局的对抗来算的一个 Elo 分。
 * 对手不是别人，是"这个词当时有多难"——FSRS 的难度加上间隔越长越值钱，
 * 于是 1200 分这条中线读作"该记住的词记住了一半"。
 *
 * 这个数**只给人看**：不写库、不改排期、不参与任何一次评分，重放一遍就能算出现在和历史最高，
 * 所以它坏了也不会影响一个词哪天回来。见 docs/adr/0030。
 */
data class PeakScore(val current: Int, val best: Int, val samples: Int) {
    /** 记录太少时这个数没有意义，界面不该管它叫"巅峰"。 */
    val usable: Boolean get() = samples >= PeakScoreReplay.MIN_SAMPLES
}

object PeakScoreReplay {
    /** 起点，也是"和对手同实力"的那条线。 */
    const val BASE = 1200.0
    const val K = 24.0
    const val MIN_SAMPLES = 20

    /** 间隔再长也只算到这么多天：一年才回一次的词不该把分推到天上。 */
    const val MAX_INTERVAL_DAYS = 240

    /** [events] 必须按时间升序（[com.immerlex.core.db.ReviewHistoryDao.recallEvents] 就是这个顺序）。 */
    fun replay(events: List<RecallEventRow>): PeakScore {
        var me = BASE
        var best = BASE
        for (event in events) {
            me += K * (outcome(event.rating) - expected(me, opponent(event)))
            if (me > best) best = me
        }
        return PeakScore(me.roundToInt(), best.roundToInt(), events.size)
    }

    /** 「困难」是想起来了但费劲，给半分——把它当答错会凭空贬低一次真实的回忆。 */
    private fun outcome(rating: Int): Double = when {
        rating >= Rating.GOOD.value -> 1.0
        rating == Rating.HARD.value -> 0.5
        else -> 0.0
    }

    private fun opponent(event: RecallEventRow): Double =
        BASE +
            (event.difficulty - DEFAULT_DIFFICULTY) * DIFFICULTY_STEP +
            minOf(event.scheduledDays, MAX_INTERVAL_DAYS) / INTERVAL_STEP

    private fun expected(me: Double, opponent: Double): Double =
        1.0 / (1.0 + 10.0.pow((opponent - me) / 400.0))

    /** FSRS 的默认难度就是 5；中线偏移按每档 50 分算，间隔按每 3 天 1 分。 */
    private const val DEFAULT_DIFFICULTY = 5.0
    private const val DIFFICULTY_STEP = 50.0
    private const val INTERVAL_STEP = 3.0
}
