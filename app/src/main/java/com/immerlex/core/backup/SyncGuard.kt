package com.immerlex.core.backup

import com.immerlex.core.db.SYNC_LINEAGE_ROW
import com.immerlex.core.db.SyncLineageEntity

/**
 * 本机学习现状的一个指纹。同步冲突判定只认它，不认任何时钟——
 * 两台设备的时钟本来就互不可信（本机曾因为模拟器时区是 GMT 而差 8 小时），
 * 而坚果云的 mtime 是服务器写的，也不能用来决定"谁的更新"。
 *
 * [extras] 是词/章/记录之外的指纹（素材、易错、故事档案、室友…）。只比相等：
 * 老血缘里这一项是空串，与任何真实指纹都不相等，所以升级后第一次下载会多问一句。
 */
data class LocalState(
    val words: Int,
    val chapters: Int,
    val reviews: Int,
    val lastReviewId: Long,
    val extras: String = SyncFingerprint.EMPTY,
) {
    /** 一台还没写过任何东西的机器，下载哪份都不会丢东西。 */
    val isEmpty: Boolean get() = words == 0 && chapters == 0 && reviews == 0 && extras == SyncFingerprint.EMPTY
}

/**
 * 本机与云端的血缘：上一次把哪一份推上去了、上一次从哪一份拉下来，
 * 以及做完那件事之后本机是什么样子。丢了这条记录，判定就只能失败成"问用户"。
 */
data class Lineage(
    val device: String = "",
    /** 本机最后确认"和云端一致"的那一份文件名；空串 = 从来没同步过。 */
    val syncedName: String = "",
    val synced: LocalState = LocalState(0, 0, 0, 0),
    val lastUploadName: String = "",
    val lastUploadSec: Long = 0,
    val lastRestoreName: String = "",
    val lastRestoreSec: Long = 0,
) {
    companion object {
        fun empty(device: String) = Lineage(device = device)
    }
}

/** 把云端（或本机安全快照）那一份拉下来之前，本机可能丢掉多少东西。 */
sealed interface DownloadVerdict {
    /** 本机自上次同步以来一个字没动过，放心下载。 */
    data object InSync : DownloadVerdict

    /** 本机还是台空机器，下载是纯得到。 */
    data object NothingToLose : DownloadVerdict

    /**
     * 本机有上次同步之后才产生的记录：[reviews] 条复习记录、[words] 个词、[chapters] 章。
     * 绝不静默下载，必须由用户在"先上传"和"仍然下载"之间选一个。
     */
    data class LocalUnsynced(val words: Int, val chapters: Int, val reviews: Int) : DownloadVerdict
}

/**
 * 手动单活跃设备同步（学完上传 / 学前下载）的判定闸门。
 * 上传永远安全：文件名带设备与秒，不会顶掉别人的那一份。
 * 下载危险，因为恢复会把整台机器的库换成那一份文件里的库。
 */
object SyncGuard {

    fun verdict(local: LocalState, lineage: Lineage): DownloadVerdict = when {
        local.isEmpty -> DownloadVerdict.NothingToLose
        lineage.syncedName.isEmpty() -> DownloadVerdict.LocalUnsynced(
            words = local.words,
            chapters = local.chapters,
            reviews = local.reviews,
        )
        local == lineage.synced -> DownloadVerdict.InSync
        else -> DownloadVerdict.LocalUnsynced(
            words = more(local.words, lineage.synced.words),
            chapters = more(local.chapters, lineage.synced.chapters),
            reviews = more(local.reviews, lineage.synced.reviews),
        )
    }

    /** 同步之后又撤销过几条，数目会往回走；"有改动"是真的，但报负数没有意义。 */
    private fun more(now: Int, then: Int): Int = (now - then).coerceAtLeast(0)
}

/** 推上去一份之后：本机现在就等于云端那一份。 */
fun Lineage.uploaded(name: String, state: LocalState, atSec: Long): Lineage =
    copy(syncedName = name, synced = state, lastUploadName = name, lastUploadSec = atSec)

/** 拉下来一份之后：同上，但记得这次是别人/旧的覆盖了我。 */
fun Lineage.restored(name: String, state: LocalState, atSec: Long): Lineage =
    copy(syncedName = name, synced = state, lastRestoreName = name, lastRestoreSec = atSec)

/**
 * 退回本机安全快照之后：本机内容与云端任何一份都不再对得上，所以"已同步"必须清掉。
 * 留成上一份云端文件名会让下一次下载以为本机没动过，直接放行、把刚退回来的内容又盖掉。
 */
fun Lineage.rolledBack(name: String, atSec: Long): Lineage =
    copy(syncedName = "", synced = LocalState(0, 0, 0, 0), lastRestoreName = name, lastRestoreSec = atSec)

fun SyncLineageEntity?.toDomain(): Lineage {
    val row = this ?: return Lineage.empty("")
    return Lineage(
        device = row.device,
        syncedName = row.syncedName,
        synced = LocalState(
            row.syncedWords,
            row.syncedChapters,
            row.syncedReviews,
            row.syncedLastReviewId,
            row.syncedExtras,
        ),
        lastUploadName = row.lastUploadName,
        lastUploadSec = row.lastUploadSec,
        lastRestoreName = row.lastRestoreName,
        lastRestoreSec = row.lastRestoreSec,
    )
}

fun Lineage.toEntity() = SyncLineageEntity(
    id = SYNC_LINEAGE_ROW,
    device = device,
    syncedName = syncedName,
    syncedWords = synced.words,
    syncedChapters = synced.chapters,
    syncedReviews = synced.reviews,
    syncedLastReviewId = synced.lastReviewId,
    syncedExtras = synced.extras,
    lastUploadName = lastUploadName,
    lastUploadSec = lastUploadSec,
    lastRestoreName = lastRestoreName,
    lastRestoreSec = lastRestoreSec,
)
