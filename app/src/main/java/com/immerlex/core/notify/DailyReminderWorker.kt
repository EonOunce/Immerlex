package com.immerlex.core.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 容器给提醒任务露出的那一小块能力。core 不认识 `AppContainer`，任务只通过这个接口拿数据，
 * 所以到期数为 0 的那天不会弹通知（docs/adr/0006 第 4 条）。
 */
interface ReminderHost {
    val reminderEnabled: Boolean
    val reminderMinuteOfDay: Int
    var lastRemindedEpochDay: Long

    suspend fun todayDueCount(now: Instant): Int
}

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as? ReminderHost ?: return Result.failure()
        try {
            if (!host.reminderEnabled) return Result.success()
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val today = LocalDate.now(zone).toEpochDay()
            // 系统把任务提前唤醒、或用户刚改过时间重新排队时，同一天不弹第二条。
            if (host.lastRemindedEpochDay == today) return Result.success()

            val due = host.todayDueCount(now)
            if (due == 0) return Result.success()

            Notifier.ensureChannels(applicationContext)
            Notifier.postDueReminder(applicationContext, due)
            host.lastRemindedEpochDay = today
            return Result.success()
        } finally {
            // 明天这个点还得再来一次，所以无论这一趟弹没弹、有没有到期词都要重新排队。
            // 排在最后：unique work 用 REPLACE，排在前头会把正在跑的这一次自己取消掉。
            if (host.reminderEnabled) rescheduleNextDay(host)
        }
    }

    private fun rescheduleNextDay(host: ReminderHost) {
        ReminderScheduler.scheduleNext(
            applicationContext,
            host.reminderMinuteOfDay.coerceIn(0, AppPreferences.MINUTES_PER_DAY - 1),
            Instant.now().plusSeconds(60),
        )
    }
}
