package com.immerlex.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit

/** 容器露给备份任务的那一小块能力，core 不认识 AppContainer。 */
interface BackupHost {
    val backupEnabled: Boolean
    suspend fun runBackup(now: Instant): BackupOutcome
}

/**
 * 每日一次自动备份。WorkManager 的周期任务只保证"至少一天一次"、给不出具体时刻，
 * 这对备份正合适：晚几小时上传和 20:30 上传没有区别，早上传反而更省流量。
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as? BackupHost ?: return Result.failure()
        // 开关被关掉时排队的这一趟直接作废，别去写一条"失败"记录吓用户。
        if (!host.backupEnabled) return Result.success()
        host.runBackup(Instant.now())
        // 失败也只记一笔、不重试：重试会把同一天的 backup_runs 刷出好几行，
        // 而明天这次本来还会再跑；真正的问题由设置页那句"上次备份失败"来说。
        return Result.success()
    }
}

object BackupScheduler {
    const val WORK_NAME = "webdav-daily-backup"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
