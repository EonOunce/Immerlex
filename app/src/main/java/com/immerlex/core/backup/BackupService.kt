package com.immerlex.core.backup

import android.content.Context
import androidx.room.RoomDatabase
import com.immerlex.core.db.BackupRunDao
import com.immerlex.core.db.BackupRunEntity
import com.immerlex.core.secret.SecretStore
import com.immerlex.core.settings.AppPreferences
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface BackupOutcome {
    /** [pruned] 是云端按保留策略删掉的份数，0 也说明真的去数过。 */
    data class Uploaded(val remoteName: String, val bytes: Long, val pruned: Int) : BackupOutcome
    data class Failed(val failure: BackupFailure, val detail: String) : BackupOutcome
}

/** 列云端目录：只有"拿到了"和"没拿到"两种，剩下的分支不该由这个调用产生。 */
sealed interface Listing {
    data class Files(val files: List<RemoteFile>) : Listing
    data class Failed(val failure: BackupFailure, val detail: String) : Listing
}

sealed interface RestoreOutcome {
    data class Restored(val name: String, val words: Int, val chapters: Int) : RestoreOutcome

    /** 校验没过：一份没验过的库绝不覆盖本地，问题清单交给界面逐条列出来。 */
    data class Refused(val name: String, val issues: List<SnapshotProblem>) : RestoreOutcome

    data class Failed(val failure: BackupFailure, val detail: String) : RestoreOutcome

    /** 本地库连接已经关掉、文件没换成的那一种：也必须重开应用，但数据还是原来的。 */
    data class RestartRequired(val detail: String) : RestoreOutcome
}

/**
 * 坚果云 WebDAV 自动备份：快照 → gzip → 上传 → 按保留策略清理，并把每次结果写进 backup_runs。
 * 密钥（应用密码）走 [SecretStore]，AI key 从来不进备份文件（docs/adr/0006 第 7 条）。
 */
class BackupService(
    private val context: Context,
    private val db: RoomDatabase,
    private val prefs: AppPreferences,
    private val password: SecretStore,
    private val runs: BackupRunDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isConfigured: Boolean
        get() = prefs.webDavUrl.isNotBlank() && prefs.webDavAccount.isNotBlank() && password.value().isNotBlank()

    suspend fun lastRun(): BackupRunEntity? = runs.latest()

    /** 开关管两件事：prefs 里的期望状态，和 WorkManager 里那份真实的排班。 */
    fun setAutoBackup(enabled: Boolean) {
        prefs.backupEnabled = enabled
        BackupScheduler.sync(context, enabled)
    }

    /** 应用密码留空表示"不改"：设置页回填不了密文，也不能因为用户没重新粘一次就把它清掉。 */
    fun saveConfig(url: String, account: String, appPassword: String) {
        prefs.webDavUrl = url
        prefs.webDavAccount = account
        if (appPassword.isNotBlank()) password.save(appPassword)
        BackupScheduler.sync(context, prefs.backupEnabled)
    }

    val hasPassword: Boolean get() = password.value().isNotBlank()

    suspend fun run(now: Instant = Instant.now()): BackupOutcome {
        if (!isConfigured) {
            return record(now, failed(BackupFailure.NOT_CONFIGURED, MISSING_CONFIG))
        }
        val outcome = try {
            upload(now.atZone(zone).toLocalDate())
        } catch (e: WebDavException) {
            BackupOutcome.Failed(e.failure, e.message.orEmpty())
        } catch (e: Exception) {
            // 快照是本地文件操作，出任何岔子都归到"本机没能做出这份备份"。
            BackupOutcome.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
        return record(now, outcome)
    }

    suspend fun listRemote(): Listing = try {
        Listing.Files(client().list().sortedByDescending { it.name })
    } catch (e: WebDavException) {
        Listing.Failed(e.failure, e.message.orEmpty())
    } catch (e: Exception) {
        Listing.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }

    /**
     * 恢复：下载 → 校验 → 才覆盖本地库。成功后本机 Room 连接已经关掉，
     * 调用方必须让应用重启一次，否则还活着的旧连接会读到一个已经不存在的库。
     */
    suspend fun restore(name: String): RestoreOutcome {
        if (!isConfigured) return RestoreOutcome.Failed(BackupFailure.NOT_CONFIGURED, MISSING_CONFIG)
        val dir = scratchDir()
        val gzip = File(dir, "restore.db.gz")
        val sqlite = File(dir, "restore.db")
        val downloaded = try {
            client().download(name, gzip)
            val unpacked = DbSnapshot.unpack(gzip, sqlite, DbSnapshot.version(db))
            if (!unpacked.usable) return RestoreOutcome.Refused(name, unpacked.issues)
            unpacked
        } catch (e: WebDavException) {
            return RestoreOutcome.Failed(e.failure, e.message.orEmpty())
        } catch (e: Exception) {
            return RestoreOutcome.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            gzip.delete()
        }
        return try {
            db.close()
            DbSnapshot.install(context, downloaded.sqlite)
            RestoreOutcome.Restored(name, downloaded.words, downloaded.chapters)
        } catch (e: Exception) {
            // 库连接已经关了，这时候无论换没换成，本机都必须重开一次应用才能再用。
            RestoreOutcome.RestartRequired("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            sqlite.delete()
        }
    }

    private suspend fun upload(day: LocalDate): BackupOutcome {
        val client = client()
        val dir = scratchDir()
        val snapshot = DbSnapshot.take(db, context, dir)
        val check = File(dir, "upload.check.db")
        try {
            val unpacked = DbSnapshot.unpack(snapshot, check, DbSnapshot.version(db))
            if (!unpacked.usable) {
                val why = unpacked.issues.joinToString { "${it.kind} ${it.detail}".trim() }
                return failed(BackupFailure.CORRUPT, "self-check: $why")
            }
            val name = BackupNaming.remoteName(day)
            client.ensureFolder()
            client.upload(name, snapshot)
            // 清理失败不能把已经传上去的备份说成失败：云端那份是真的。
            val pruned = runCatching { prune(client) }.getOrDefault(0)
            return BackupOutcome.Uploaded(name, snapshot.length(), pruned)
        } finally {
            snapshot.delete()
            check.delete()
        }
    }

    /** 最近 30 份 + 每月最后一份，其余删掉（docs/adr/0006 第 6 条）。 */
    private suspend fun prune(client: WebDavClient): Int {
        val doomed = Retention.toDelete(client.list().map { it.name })
        doomed.forEach { client.delete(it) }
        return doomed.size
    }

    private suspend fun record(now: Instant, outcome: BackupOutcome): BackupOutcome {
        val failed = outcome as? BackupOutcome.Failed
        runs.insert(
            BackupRunEntity(
                atSec = now.epochSecond,
                ok = outcome is BackupOutcome.Uploaded,
                remoteName = (outcome as? BackupOutcome.Uploaded)?.remoteName.orEmpty(),
                bytes = (outcome as? BackupOutcome.Uploaded)?.bytes ?: 0L,
                // 措辞属于 UI，库里只存"枚举名: 原始信息"，设置页再把它拆开。
                detail = failed?.let { "${it.failure.name}: ${it.detail.take(MAX_STORED_DETAIL)}" }.orEmpty(),
            ),
        )
        return outcome
    }

    private fun failed(failure: BackupFailure, detail: String) = BackupOutcome.Failed(failure, detail)

    private fun client(): WebDavClient =
        WebDavClient(prefs.webDavUrl, prefs.webDavAccount, password.value())

    private fun scratchDir(): File = File(context.cacheDir, "backup").apply { mkdirs() }

    private companion object {
        const val MISSING_CONFIG = "url / account / app password"
        const val MAX_STORED_DETAIL = 160
    }
}
