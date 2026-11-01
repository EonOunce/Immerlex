package com.immerlex.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import com.immerlex.core.db.BackupRunDao
import com.immerlex.core.db.BackupRunEntity
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.SyncLineageDao
import com.immerlex.core.db.SyncLineageEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.secret.SecretStore
import com.immerlex.core.settings.AppPreferences
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    /**
     * [safetyName] 是覆盖本机之前留在本机的那一份。
     * [lineageWritten] 为 false 表示血缘没写进换上的那一份（老备份没有这张表）：数据已经对了，
     * 只是下一次下载会多问一句"本机动过没有"。
     */
    data class Restored(
        val name: String,
        val words: Int,
        val chapters: Int,
        val safetyName: String = "",
        val lineageWritten: Boolean = true,
    ) : RestoreOutcome

    /** 校验没过：一份没验过的库绝不覆盖本地，问题清单交给界面逐条列出来。 */
    data class Refused(val name: String, val issues: List<SnapshotProblem>) : RestoreOutcome

    data class Failed(val failure: BackupFailure, val detail: String) : RestoreOutcome

    /** 本地库连接已经关掉、文件没换成的那一种：也必须重开应用，但数据还是原来的。 */
    data class RestartRequired(val detail: String) : RestoreOutcome
}

/**
 * 坚果云 WebDAV 备份与多设备同步：快照 → gzip → 上传 → 按保留策略清理，并把每次结果写进 backup_runs。
 * 密钥（应用密码）走 [SecretStore]，AI key 从来不进备份文件（docs/adr/0006 第 7 条）。
 *
 * 手动单活跃设备（学完上传 / 学前下载）下，上传永远安全——文件名带设备与秒，不会顶掉别人的那一份；
 * 下载才危险，所以它前面有三道东西：本机安全快照、[verdict] 的冲突判定、校验不过绝不动本机库。
 */
class BackupService(
    private val context: Context,
    private val db: RoomDatabase,
    private val prefs: AppPreferences,
    private val password: SecretStore,
    private val runs: BackupRunDao,
    private val words: WordDao,
    private val chapters: ChapterDao,
    private val logs: ReviewLogDao,
    private val lineage: SyncLineageDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * 自动备份的 Worker 与设置页上的按钮是同一个进程里的两条协程。
     * 快照、换库都必须一个一个来：换库中途再来一次快照，拿到的是半个库。
     * 快照、复制、换文件都是阻塞 IO，所以整段挪到 IO 上跑，别占着主线程。
     */
    private val gate = Mutex()

    private suspend fun <T> guarded(block: suspend () -> T): T =
        gate.withLock { withContext(Dispatchers.IO) { block() } }

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

    /** 这台机器在文件名里叫什么。洗过一次，所以填中文或机型全称都不会产出传不上去的名字。 */
    val deviceTag: String get() = BackupNaming.deviceTag(prefs.deviceName)

    fun saveDeviceName(name: String) {
        prefs.deviceName = name
    }

    val hasPassword: Boolean get() = password.value().isNotBlank()

    suspend fun run(now: Instant = Instant.now()): BackupOutcome = guarded {
        if (!isConfigured) {
            return@guarded record(now, failed(BackupFailure.NOT_CONFIGURED, MISSING_CONFIG))
        }
        val outcome = try {
            upload(now)
        } catch (e: WebDavException) {
            BackupOutcome.Failed(e.failure, e.message.orEmpty())
        } catch (e: Exception) {
            // 快照是本地文件操作，出任何岔子都归到"本机没能做出这份备份"。
            BackupOutcome.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
        record(now, outcome)
    }

    suspend fun listRemote(): Listing = withContext(Dispatchers.IO) {
        try {
            Listing.Files(BackupNaming.sorted(client().list()))
        } catch (e: WebDavException) {
            Listing.Failed(e.failure, e.message.orEmpty())
        } catch (e: Exception) {
            Listing.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    /** 本机自己留的、覆盖之前那一瞬间的快照，新到旧。不联网，设置页一打开就能读到。 */
    fun localCopies(): List<RemoteFile> = SafetyCopies.list(context)

    /**
     * 把云端那一份拉下来之前，本机会不会丢掉刚写的记录。只看本机指纹和血缘，不看任何时钟。
     * 判定不了（没血缘）就一律报"有东西要丢"——宁可多问一句。
     */
    suspend fun verdict(): DownloadVerdict = guarded {
        SyncGuard.verdict(localState(), readLineage().toDomain())
    }

    /**
     * 恢复云端那一份：下载 → 校验 → 留本机快照 → 才覆盖本地库。成功后本机 Room 连接已经关掉，
     * 调用方必须让应用重启一次，否则还活着的旧连接会读到一个已经不存在的库。
     */
    suspend fun restore(name: String): RestoreOutcome = guarded {
        if (!isConfigured) return@guarded RestoreOutcome.Failed(BackupFailure.NOT_CONFIGURED, MISSING_CONFIG)
        val dir = scratchDir()
        val gzip = File(dir, "restore.db.gz")
        val sqlite = File(dir, "restore.db")
        val downloaded = try {
            client().download(name, gzip)
            val unpacked = DbSnapshot.unpack(gzip, sqlite, DbSnapshot.version(db))
            if (!unpacked.usable) return@guarded RestoreOutcome.Refused(name, unpacked.issues)
            unpacked
        } catch (e: WebDavException) {
            return@guarded RestoreOutcome.Failed(e.failure, e.message.orEmpty())
        } catch (e: Exception) {
            return@guarded RestoreOutcome.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            gzip.delete()
        }
        install(name, downloaded.sqlite, downloaded, cloud = true)
    }

    /** 退回本机安全快照：同样的校验与覆盖，只是不联网。当前这台机器照样先留一份。 */
    suspend fun restoreLocal(name: String): RestoreOutcome = guarded {
        val source = SafetyCopies.file(context, name)
            ?: return@guarded RestoreOutcome.Failed(BackupFailure.CORRUPT, "unknown local copy: $name")
        val sqlite = File(scratchDir(), "local-restore.db")
        val downloaded = try {
            val unpacked = DbSnapshot.unpack(source, sqlite, DbSnapshot.version(db))
            if (!unpacked.usable) return@guarded RestoreOutcome.Refused(name, unpacked.issues)
            unpacked
        } catch (e: Exception) {
            return@guarded RestoreOutcome.Failed(BackupFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
        install(name, downloaded.sqlite, downloaded, cloud = false)
    }

    private suspend fun upload(now: Instant): BackupOutcome {
        val client = client()
        val dir = scratchDir()
        val at = now.atZone(zone).toLocalDateTime()
        // 先记本机指纹再取快照：反过来会把"快照之后又写的那几条"算进云端已有的份数里，
        // 于是下次下载该问的时候不问了。宁可多问一句。
        val before = localState()
        val snapshot = DbSnapshot.take(db, context, dir)
        val check = File(dir, "upload.check.db")
        try {
            val unpacked = DbSnapshot.unpack(snapshot, check, DbSnapshot.version(db))
            if (!unpacked.usable) {
                val why = unpacked.issues.joinToString { "${it.kind} ${it.detail}".trim() }
                return failed(BackupFailure.CORRUPT, "self-check: $why")
            }
            val name = BackupNaming.remoteName(at, deviceTag, DbSnapshot.version(db))
            client.ensureFolder()
            client.upload(name, snapshot)
            // 清理失败不能把已经传上去的备份说成失败：云端那份是真的。
            val pruned = runCatching { prune(client) }.getOrDefault(0)
            writeLineage(readLineage().toDomain().uploaded(name, before, now.epochSecond))
            return BackupOutcome.Uploaded(name, snapshot.length(), pruned)
        } finally {
            snapshot.delete()
            check.delete()
        }
    }

    /**
     * 校验过了才走到这里：留一份本机快照 → 关连接 → 换文件 → 把血缘写进换上的那一份。
     *
     * 本机快照留不下就整件事不做：覆盖之后没有任何退路，这一份不能省。
     * 换文件失败时 DbFileSwap 已把旧库放回原位，但连接关过了，应用仍须重开一次。
     * 血缘写不进去只影响"下一次下载要不要问"，数据本身已经对了，所以不把它算成失败。
     */
    private suspend fun install(name: String, sqlite: File, from: Unpacked, cloud: Boolean): RestoreOutcome {
        val kept = try {
            SafetyCopies.take(db, context, LocalDateTime.now(zone))
        } catch (e: Exception) {
            sqlite.delete()
            return RestoreOutcome.Failed(
                BackupFailure.LOCAL,
                "safety copy: ${e.javaClass.simpleName}: ${e.message.orEmpty()}",
            )
        }
        val now = Instant.now().epochSecond
        // 血缘必须在关连接之前读；关掉之后这条连接上什么都不该再问。
        val previous = readLineage().toDomain()
        val next = if (cloud) previous.restored(name, from.asLocalState(), now) else previous.rolledBack(name, now)
        try {
            db.close()
            DbSnapshot.install(context, sqlite)
        } catch (e: Exception) {
            return RestoreOutcome.RestartRequired("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            sqlite.delete()
        }
        val written = writeLineageInto(context.getDatabasePath(DbSnapshot.DB_NAME), next)
        runCatching { SafetyCopies.prune(context) }
        return RestoreOutcome.Restored(name, from.words, from.chapters, kept.name, written)
    }

    /**
     * 换进来的库可能比现在旧（v14 之前连血缘表都没有），而且旧的 Room 连接已经关掉、
     * 新库还没被 Room 打开过，所以直接开文件写。缺哪列就少写哪列；整件事失败返回 false。
     */
    private fun writeLineageInto(sqlite: File, value: Lineage): Boolean = runCatching {
        SQLiteDatabase.openDatabase(sqlite.path, null, SQLiteDatabase.OPEN_READWRITE).use { file ->
            val columns = mutableSetOf<String>()
            file.rawQuery("PRAGMA table_info(`sync_lineage`)", null).use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            if (columns.isEmpty()) return@use false
            val row = value.copy(device = deviceTag).toEntity()
            val pairs = listOf(
                "id" to row.id,
                "device" to row.device,
                "syncedName" to row.syncedName,
                "syncedWords" to row.syncedWords,
                "syncedChapters" to row.syncedChapters,
                "syncedReviews" to row.syncedReviews,
                "syncedLastReviewId" to row.syncedLastReviewId,
                "syncedExtras" to row.syncedExtras,
                "lastUploadName" to row.lastUploadName,
                "lastUploadSec" to row.lastUploadSec,
                "lastRestoreName" to row.lastRestoreName,
                "lastRestoreSec" to row.lastRestoreSec,
            ).filter { (column, _) -> column in columns }
            val names = pairs.joinToString(", ") { "`${it.first}`" }
            val marks = pairs.joinToString(", ") { "?" }
            file.execSQL(
                "INSERT OR REPLACE INTO `sync_lineage` ($names) VALUES ($marks)",
                pairs.map { it.second }.toTypedArray(),
            )
            true
        }
    }.getOrDefault(false)

    /** 最近 [Retention.KEEP_RECENT] 份 + 每月最后一份，其余删掉（docs/adr/0006 第 6 条）。 */
    private suspend fun prune(client: WebDavClient): Int {
        val doomed = Retention.toDelete(client.list().map { it.name })
        doomed.forEach { client.delete(it) }
        return doomed.size
    }

    private suspend fun localState(): LocalState {
        val activity = logs.activity()
        return LocalState(
            words = words.totalCount(),
            chapters = chapters.totalCount(),
            reviews = activity.n,
            lastReviewId = activity.lastId,
            // 指纹是一串同步的 COUNT 查询；调用它的两条路（上传、判定）都已经在 IO 上。
            extras = SyncFingerprint.of(db),
        )
    }

    private suspend fun readLineage(): SyncLineageEntity? = lineage.get()

    private suspend fun writeLineage(value: Lineage) = lineage.put(value.copy(device = deviceTag).toEntity())

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
