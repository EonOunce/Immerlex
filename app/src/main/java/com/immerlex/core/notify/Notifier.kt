package com.immerlex.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.immerlex.app.MainActivity
import com.immerlex.app.R

/**
 * 通知只有两条：每日到期提醒、写作进度。渠道一次建好，文案只允许陈述此刻真的存在的状态
 * （docs/adr/0006 第 4 条）。
 */
object Notifier {
    const val CHANNEL_DAILY = "daily_review"
    const val CHANNEL_GENERATION = "generation"

    /** 提醒与写作各占一个 id：同一件事重复触发时更新而不是叠加。 */
    const val ID_DAILY = 2001
    const val ID_GENERATION = 2002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val daily = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.channel_daily_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_daily_desc) }
        val generation = NotificationChannel(
            CHANNEL_GENERATION,
            context.getString(R.string.channel_generation_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_generation_desc) }
        manager.createNotificationChannel(daily)
        manager.createNotificationChannel(generation)
    }

    fun postDueReminder(context: Context, dueCount: Int) {
        notify(
            context,
            ID_DAILY,
            CHANNEL_DAILY,
            context.getString(R.string.notify_due_title, dueCount),
            context.getString(R.string.notify_due_body),
            ongoing = false,
        )
    }

    fun postWriting(context: Context) {
        notify(
            context,
            ID_GENERATION,
            CHANNEL_GENERATION,
            context.getString(R.string.notify_writing_title),
            context.getString(R.string.notify_writing_body),
            ongoing = true,
        )
    }

    fun postWritten(context: Context, number: Int, title: String) {
        notify(
            context,
            ID_GENERATION,
            CHANNEL_GENERATION,
            context.getString(R.string.notify_written_title, number),
            title,
            ongoing = false,
        )
    }

    /** 写作没成：把进行中的那条换成一句实话，别让它一直挂着转圈。 */
    fun postWriteFailed(context: Context) {
        notify(
            context,
            ID_GENERATION,
            CHANNEL_GENERATION,
            context.getString(R.string.notify_write_failed_title),
            context.getString(R.string.notify_write_failed_body),
            ongoing = false,
        )
    }

    fun cancelWriting(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_GENERATION)
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        ongoing: Boolean,
    ) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOngoing(ongoing)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
