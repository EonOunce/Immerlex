package com.immerlex.core.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * 提醒按"当天设定时刻的一次性任务"排，跑完自己排明天。
 * WorkManager 的周期任务只有"每 24 小时"、没有"每天几点"，用它会漂到凌晨。
 */
object ReminderScheduler {
    const val WORK_NAME = "daily-review-reminder"

    fun scheduleNext(context: Context, minuteOfDay: Int, now: Instant = Instant.now()) {
        val delay = secondsUntilNext(minuteOfDay, now, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 纯函数：设定时刻还没过就是今晚，已经过了就是明天同一刻。跨夏令时靠 atStartOfDay 摆正。 */
    fun secondsUntilNext(minuteOfDay: Int, now: Instant, zone: ZoneId): Long {
        val local = now.atZone(zone)
        val scheduledToday = local.toLocalDate().atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
        val next = if (scheduledToday.toEpochSecond() > local.toEpochSecond()) scheduledToday
        else scheduledToday.plusDays(1)
        return max(1L, next.toEpochSecond() - local.toEpochSecond())
    }
}
