package com.immerlex.core.notify

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.immerlex.core.settings.AppPreferences

/**
 * 设置页改提醒只跟这个对象打交道，ViewModel 因此不用碰 Context。
 * 改一次就重新排队一次：到点提醒靠"今天设定时刻的一次性任务"，时刻变了旧任务必须作废。
 */
class ReminderControl(private val context: Context, private val prefs: AppPreferences) {

    var enabled: Boolean
        get() = prefs.reminderEnabled
        set(value) {
            prefs.reminderEnabled = value
            if (value) ReminderScheduler.scheduleNext(context, prefs.reminderMinuteOfDay)
            else ReminderScheduler.cancel(context)
        }

    var minuteOfDay: Int
        get() = prefs.reminderMinuteOfDay
        set(value) {
            prefs.reminderMinuteOfDay = value
            if (prefs.reminderEnabled) ReminderScheduler.scheduleNext(context, value)
        }

    /** 系统层面把本应用的通知渠道全关了（Android 13 拒绝授权或用户在设置里停用都会这样）。 */
    fun areNotificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 用户刚点完"允许通知"：这时候当天可能已经过了设定时刻，重新排到下一次。 */
    fun rearm() {
        if (prefs.reminderEnabled) ReminderScheduler.scheduleNext(context, prefs.reminderMinuteOfDay)
    }
}
