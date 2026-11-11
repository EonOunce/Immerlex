package com.immerlex.core.schedule

import com.immerlex.core.db.RecallEventRow

/**
 * 影子对决：巅峰分这一周最高到过哪，跟上一周比。
 *
 * 对手还是只有以前的自己——不排行、不上传，一周的界线就是日历上的周一。
 * 一周里一条记录都没有就是 null：不拿 1200 这个起点冒充"这周打过"。
 */
data class WeekDuel(val thisWeek: Int?, val lastWeek: Int?) {
    /** 两边都打过才有得比；只有一边时界面说的是"还欠一场"，不报涨跌。 */
    val change: Int? get() = if (thisWeek != null && lastWeek != null) thisWeek - lastWeek else null
}

object WeeklyDuel {
    /** 1970-01-01 是周四，+3 把 epochDay 折成"以周一为始的第几周"。 */
    fun weekOf(epochDay: Long): Long = (epochDay + 3).floorDiv(DAYS_PER_WEEK)

    /**
     * [events] 按时间升序，[weeks] 与它一一对应、写的是每一条落在第几周，
     * [thisWeek] 是今天所在的那一周。分出自 [PeakScoreReplay.trajectory]，
     * 所以这里读的仍是同一份复习记录，没有第二套账。
     */
    fun of(events: List<RecallEventRow>, weeks: List<Long>, thisWeek: Long): WeekDuel {
        val scores = PeakScoreReplay.trajectory(events)
        var best = MISSING
        var last = MISSING
        weeks.forEachIndexed { i, week ->
            when (week) {
                thisWeek -> best = maxOf(best, scores[i])
                thisWeek - 1 -> last = maxOf(last, scores[i])
            }
        }
        return WeekDuel(best.orNull(), last.orNull())
    }

    private const val MISSING = Int.MIN_VALUE
    private const val DAYS_PER_WEEK = 7L

    private fun Int.orNull() = if (this == MISSING) null else this
}
