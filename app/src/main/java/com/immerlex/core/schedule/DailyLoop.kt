package com.immerlex.core.schedule

import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * 打卡与补卡的规则层（docs/adr/0006 第 2 条）。纯函数，输入全是已经存在的事实：
 * 哪些天打过卡来自 `review_logs`，哪些天清空过到期词、哪些天用掉了补卡来自 `daily_loop`。
 */
data class DailyLoopInput(
    val today: LocalDate,
    /** 当天给任意 1 个词打过分——即"打卡"。 */
    val reviewedDays: Set<Long>,
    val clearedDays: Set<Long>,
    val makeupDays: Set<Long>,
    val dueCount: Int,
    val reviewedToday: Int,
)

data class DailyInfo(
    val dueCount: Int,
    val reviewedToday: Int,
    val streakDays: Int,
    val checkedInToday: Boolean,
    /** 今天这次补卡成不成立：昨天缺卡，且今天把当日到期词清完了。 */
    val makeupGrantedToday: Boolean,
    /** 给 UI 说的"本周补卡：未用/已用"，含今天刚用掉的那次。 */
    val makeupUsedThisWeek: Boolean,
    /** 昨天缺卡、今天还能靠清空到期词补上。 */
    val makeupPendingToday: Boolean,
)

/** 回看上限：连续天数不会真的数到一千天，再多就是数据异常。 */
const val STREAK_LOOKBACK_DAYS = 400

fun dailyInfoOf(input: DailyLoopInput): DailyInfo {
    val today = input.today.toEpochDay()
    val yesterday = today - 1
    val clearedToday = today in input.clearedDays
    val weekToday = isoWeek(today)
    // 昨天之前没有任何打卡，就谈不上"断了"——首日用清空到期词也换不来补卡。
    val hadStreakBefore = input.reviewedDays.any { it < yesterday }
    val missedYesterday = yesterday !in input.reviewedDays && hadStreakBefore
    // 本周额度只能被"别的日子"用掉一次；今天这次是否成立在下面单独判。
    val spentEarlierThisWeek = input.makeupDays.any { it != today && isoWeek(it) == weekToday }
    val makeupGrantedToday = missedYesterday && clearedToday && !spentEarlierThisWeek

    val streak = streakOf(
        reviewed = input.reviewedDays,
        makeupDays = if (makeupGrantedToday) input.makeupDays + today else input.makeupDays,
        today = today,
    )
    return DailyInfo(
        dueCount = input.dueCount,
        reviewedToday = input.reviewedToday,
        streakDays = streak,
        checkedInToday = today in input.reviewedDays,
        makeupGrantedToday = makeupGrantedToday,
        makeupUsedThisWeek = spentEarlierThisWeek || makeupGrantedToday,
        makeupPendingToday = missedYesterday && !spentEarlierThisWeek && !clearedToday,
    )
}

/**
 * 从今天往回数：今天还没打卡不算断签（只是不计入），缺口只有在它的次日真的补过卡时才算续上，
 * 其余任何缺口一律结束连续。当场能补的那一次已经由调用方判好并放进 [makeupDays]。
 */
private fun streakOf(reviewed: Set<Long>, makeupDays: Set<Long>, today: Long): Int {
    var day = if (today in reviewed) today else today - 1
    var days = 0
    val floorDay = maxOf(today - STREAK_LOOKBACK_DAYS, reviewed.minOrNull() ?: day)
    while (day >= floorDay) {
        if (day in reviewed) {
            days++
            day--
            continue
        }
        if (day + 1 !in makeupDays) break
        days++
        day--
    }
    return days
}

private fun isoWeek(epochDay: Long): Long = with(LocalDate.ofEpochDay(epochDay)) {
    get(WeekFields.ISO.weekBasedYear()).toLong() * 100 + get(WeekFields.ISO.weekOfWeekBasedYear())
}
