package com.immerlex.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * 恢复备份换掉了本机数据库文件，而 Room 的连接已经关闭：进程继续活着只会读到已经不存在的库。
 * 所以这里排一次真实的重新启动，然后把进程杀掉——返回上一个页面并不能解决它。
 */
fun Context.restartApp() {
    val launch = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    getSystemService(Context.ALARM_SERVICE)
        ?.let { it as AlarmManager }
        ?.set(AlarmManager.RTC, System.currentTimeMillis() + RESTART_DELAY_MS, pending)
    exitProcess(0)
}

private const val RESTART_DELAY_MS = 400L
