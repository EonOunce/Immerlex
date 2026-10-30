package com.immerlex.core.roommate

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Settlement(val balance: Int, val earnedToday: Int)

/**
 * 把学习记录换成小鱼干。只在记录过了撤销窗口之后才结算——8 秒内还能撤销的
 * 日志永远不可能已经发过钱，所以撤销不会留下"凭空花掉"的余额。
 *
 * 已封口的整天依次结算；今天只结算"最后一次碰到该词已超过 8 秒"的那些。
 * 进程被杀、重开应用、换页都不会重发：账本上的 dedupeKey 唯一。
 */
class RoommateSettlement(private val repo: RoommateRepository) {
    private val mutex = Mutex()

    suspend fun settle(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Settlement = mutex.withLock {
        val today = now.atZone(zone).toLocalDate().toEpochDay()
        var state = repo.state()
        if (state.lastSettledDay == 0L) {
            // 第一次启用不从历史记录里倒补一笔巨款：从昨天开始算。
            state = state.copy(lastSettledDay = today - 1)
            repo.saveState(state)
        }
        var day = state.lastSettledDay + 1
        while (day <= today - 1) {
            creditDay(day, zone, cutoffSec = Long.MAX_VALUE, nowSec = now.epochSecond)
            day++
        }
        if (state.lastSettledDay < today - 1) {
            repo.saveState(repo.state().copy(lastSettledDay = today - 1))
        }
        creditDay(today, zone, cutoffSec = now.epochSecond - RewardPolicy.UNDO_GRACE_SECONDS, nowSec = now.epochSecond)
        Settlement(repo.balance(), repo.earnedOn(today))
    }

    private suspend fun creditDay(day: Long, zone: ZoneId, cutoffSec: Long, nowSec: Long) {
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toEpochSecond()
        val end = LocalDate.ofEpochDay(day + 1).atStartOfDay(zone).toEpochSecond()
        val touches = repo.touches(start, end)
        var credited = repo.wordCredits(day)
        for (touch in touches) {
            if (touch.lastSec > cutoffSec) continue
            // 已记过的词数决定这一条落在哪一档；没有每日封顶，只是越往后越少。
            val amount = RewardPolicy.rewardAt(credited + 1)
            if (repo.creditWord(day, touch.wordKey, nowSec, amount)) credited++
        }
        if (credited >= RewardPolicy.MILESTONE_AT) {
            repo.creditMilestone(day, nowSec)
        }
    }
}
