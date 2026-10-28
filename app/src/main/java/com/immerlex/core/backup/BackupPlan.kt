package com.immerlex.core.backup

import java.time.LocalDate
import java.time.YearMonth

/** 云端的一个备份文件：名字与大小，PROPFIND 能给出的就这两样。 */
data class RemoteFile(val name: String, val sizeBytes: Long)

/**
 * 备份文件按日期命名铺在同一个目录里，所以"版本历史"就是文件名列表——
 * 坚果云 WebDAV 自己不提供任何历史（docs/adr/0006 第 6 条）。
 */
object BackupNaming {
    /** 预填地址只到 /dav/，这一个目录由应用自己建，用户不用去网页端手动新建。 */
    const val COLLECTION = "Immerlex"

    private val NAME = Regex("^immerlex-(\\d{4}-\\d{2}-\\d{2})\\.db\\.gz$")

    /** 同一天再备一次是覆盖同名文件，云端因此永远一天最多一份。 */
    fun remoteName(day: LocalDate): String = "immerlex-$day.db.gz"

    /** 不是这个形状的（别人往同一目录塞的文件、目录本身）一律返回 null，绝不参与删除。 */
    fun dateOf(rawName: String): LocalDate? {
        val name = rawName.substringAfterLast('/')
        val matched = NAME.matchEntire(name) ?: return null
        return runCatching { LocalDate.parse(matched.groupValues[1]) }.getOrNull()
    }
}

/**
 * 保留策略：最近 [KEEP_RECENT] 份 + 每个更早月份里最新的那一份，其余删除。
 * 月末那份不受 30 份上限约束——它是"上个月确实存在过"的唯一凭据。
 */
object Retention {
    const val KEEP_RECENT = 30

    fun toDelete(names: List<String>): List<String> {
        val dated = names.mapNotNull { name -> BackupNaming.dateOf(name)?.let { it to name } }
            .sortedByDescending { (day, _) -> day }
        if (dated.size <= KEEP_RECENT) return emptyList()

        val keep = dated.take(KEEP_RECENT).mapTo(mutableSetOf()) { it.second }
        dated.drop(KEEP_RECENT)
            .groupBy { (day, _) -> YearMonth.from(day) }
            .forEach { (_, files) -> keep += files.maxByOrNull { (day, _) -> day }!!.second }
        return dated.map { it.second }.filterNot { it in keep }
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
