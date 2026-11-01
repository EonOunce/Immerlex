package com.immerlex.core.backup

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 云端的一个备份文件：名字与大小，PROPFIND 能给出的就这两样。 */
data class RemoteFile(val name: String, val sizeBytes: Long)

/**
 * 一份备份在它自己的名字里写清了来历：哪天、当天第几秒、哪台设备、库是第几版。
 * 坚果云 WebDAV 没有版本历史（docs/adr/0006 第 6 条），所以这些字段只能靠文件名活着。
 */
data class SnapshotKey(
    val name: String,
    val day: LocalDate,
    /** 当天第几秒。老命名只有日期，读成 null——它一定是那天最早的那一份。 */
    val secondOfDay: Int?,
    /** 老命名没写设备，读成空串。 */
    val device: String,
    /** 老命名没写库版本，读成 0 = 不知道。 */
    val dbVersion: Int,
) {
    /** 新旧混在一起也要排得出来：先按时刻，同刻（老命名的 0）再按名字。 */
    val rank: Long get() = day.toEpochDay() * SECONDS_PER_DAY + (secondOfDay ?: 0)

    val isLegacy: Boolean get() = secondOfDay == null

    /** 界面要说"几点"，时刻从名字里读出来给它；老命名没有时刻，就是空串。 */
    val hourMinute: String
        get() = secondOfDay?.let { "%02d:%02d".format(it / 3600, it % 3600 / 60) }.orEmpty()

    companion object {
        const val SECONDS_PER_DAY = 86_400L

        val NEWEST_FIRST: Comparator<SnapshotKey> =
            compareByDescending<SnapshotKey> { it.rank }.thenByDescending { it.name }
    }
}

object BackupNaming {
    /** 预填地址只到 /dav/，这一个目录由应用自己建，用户不用去网页端手动新建。 */
    const val COLLECTION = "Immerlex"

    private const val PREFIX = "immerlex-"
    private const val SUFFIX = ".db.gz"
    private const val MAX_DEVICE = 24

    private val NAME = Regex(
        "^immerlex-(\\d{4}-\\d{2}-\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})-([a-z0-9][a-z0-9_-]{0,23})-v(\\d+))?\\.db\\.gz$",
    )
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
    private val ILLEGAL = Regex("[^a-z0-9_-]+")
    private val DASHES = Regex("-{2,}")

    /**
     * 名字里带秒和设备，所以"同一天再传一次"不再覆盖上一份——多设备手动同步最怕的就是
     * 后传的那一份把前一份悄悄顶掉，而云端没有历史可以找回。
     */
    fun remoteName(at: LocalDateTime, device: String, dbVersion: Int): String =
        "$PREFIX${at.format(STAMP)}-${deviceTag(device)}-v$dbVersion$SUFFIX"

    /** 设备名要进文件名，也要进 URL 路径，所以只留小写字母数字和 `-` `_`。 */
    fun deviceTag(raw: String): String {
        val cleaned = DASHES.replace(raw.lowercase().trim().replace(ILLEGAL, "-").trim('-'), "-").take(MAX_DEVICE).trim('-')
        return cleaned.ifBlank { "device" }
    }

    /** 不是这个形状的（别人往同一目录塞的文件、目录本身）一律返回 null，绝不参与删除。 */
    fun keyOf(rawName: String): SnapshotKey? {
        val name = rawName.substringAfterLast('/')
        val matched = NAME.matchEntire(name) ?: return null
        val day = runCatching { LocalDate.parse(matched.groupValues[1]) }.getOrNull() ?: return null
        val stamp = matched.groupValues[2]
        // v2.5 及以前的文件只有日期：它认得，但读不出设备与库版本。
        if (stamp.isEmpty()) return SnapshotKey(name, day, null, "", 0)
        val hour = stamp.toInt()
        val minute = matched.groupValues[3].toInt()
        val second = matched.groupValues[4].toInt()
        // 时刻越界的文件名不是本应用写的，不能让它进排序与删除。
        if (hour > 23 || minute > 59 || second > 59) return null
        return SnapshotKey(
            name = name,
            day = day,
            secondOfDay = hour * 3600 + minute * 60 + second,
            device = matched.groupValues[5],
            dbVersion = matched.groupValues[6].toIntOrNull() ?: 0,
        )
    }

    /** 云端"版本历史"就是这份列表，界面按新到旧排；名字排序与时刻排序在这里等价。 */
    fun sorted(files: List<RemoteFile>): List<RemoteFile> = files.sortedWith(
        compareByDescending { keyOf(it.name)?.rank ?: -1L },
    )
}

/**
 * 保留策略：最近 [KEEP_RECENT] 份 + 每个更早月份里最新的那一份，其余删除。
 * 月末那份不受上限约束——它是"上个月确实存在过"的唯一凭据。
 * 一天可以有多份，所以数的是份数而不是天数。
 */
object Retention {
    const val KEEP_RECENT = 60

    fun toDelete(names: List<String>): List<String> {
        val newestFirst = names.mapNotNull(BackupNaming::keyOf).sortedWith(SnapshotKey.NEWEST_FIRST)
        if (newestFirst.size <= KEEP_RECENT) return emptyList()

        val keep = newestFirst.take(KEEP_RECENT).mapTo(mutableSetOf()) { it.name }
        newestFirst.drop(KEEP_RECENT)
            .groupBy { YearMonth.from(it.day) }
            // 已经按新到旧排过，所以每组第一个就是那个月最新的。
            .forEach { (_, older) -> keep += older.first().name }
        return newestFirst.map { it.name }.filterNot { it in keep }
    }
}

/**
 * PROPFIND 的 207 响应只有 href 与 getcontentlength 有用，其余（displayname、
 * creationdate、各厂商自定义字段）一概不管。前缀（D: / d: / dav:）由服务器决定，所以匹配时忽略前缀。
 */
object Propfind {
    // 属性组一律非捕获：下面按 groupValues[1] 取正文，多一个捕获组就会全体错位。
    private val RESPONSE = Regex(
        "<[A-Za-z0-9_.-]*:?response(?:\\s[^>]*)?>(.*?)</[A-Za-z0-9_.-]*:?response\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HREF = Regex(
        "<[A-Za-z0-9_.-]*:?href(?:\\s[^>]*)?>([^<]*)</[A-Za-z0-9_.-]*:?href\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val LENGTH = Regex(
        "<[A-Za-z0-9_.-]*:?getcontentlength[^>]*>([^<]*)</",
        RegexOption.IGNORE_CASE,
    )

    fun parse(xml: String): List<RemoteFile> = RESPONSE.findAll(xml).mapNotNull { block ->
        val href = HREF.find(block.value)?.groupValues?.get(1)?.trim()?.let(::decode) ?: return@mapNotNull null
        val size = LENGTH.find(block.value)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
        RemoteFile(href.substringAfterLast('/'), size)
    }.filter { it.name.isNotEmpty() }.toList()

    /** href 是百分号编码的路径：坚果云把中文目录名写成 %E2%80%A6。 */
    private fun decode(href: String): String =
        runCatching { java.net.URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
}
