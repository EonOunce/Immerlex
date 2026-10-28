package com.immerlex.core.notify

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 提醒时刻的换算是纯函数，Android 那半边（WorkManager）没法在 JVM 里测。 */
class ReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    private fun secondsUntil(minuteOfDay: Int, now: Instant) =
        ReminderScheduler.secondsUntilNext(minuteOfDay, now, zone)

    @Test
    fun beforeTheTimeWaitsUntilTonight() {
        assertEquals(90 * 60L, secondsUntil(20 * 60 + 30, at(2026, 10, 28, 19, 0)))
    }

    @Test
    fun afterTheTimeWaitsUntilTomorrow() {
        val now = at(2026, 10, 28, 21, 0)
        // 21:00 → 明天 20:30：剩下 3 小时到今天午夜再加 20.5 小时。
        assertEquals(3 * 3600L + 20 * 3600L + 30 * 60L, secondsUntil(20 * 60 + 30, now))
    }

    @Test
    fun exactlyAtTheTimeRollsForwardOneDay() {
        val now = at(2026, 10, 28, 20, 30)
        assertEquals(24 * 3600L, secondsUntil(20 * 60 + 30, now))
    }

    @Test
    fun midnightReminderIsTheNextMidnight() {
        assertEquals(4 * 3600L, secondsUntil(0, at(2026, 10, 28, 20, 0)))
    }

    @Test
    fun lastMinuteOfDayStillFitsToday() {
        assertEquals(60L, secondsUntil(23 * 60 + 59, at(2026, 10, 28, 23, 58)))
    }
}
