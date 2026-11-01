package com.immerlex.core.backup

import android.content.Context
import androidx.room.RoomDatabase
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 下载/恢复之前先在本机留一份当前库。云端没有版本历史，而"覆盖本机库"这一步
 * 一旦发生就没有回头路——留最近 [KEEP] 份，用户发现拉错了还能就地退回去。
 *
 * 存在 `filesDir` 而不是 `cacheDir`：缓存会被系统按空间需要清掉，安全快照被清掉就等于没留。
 */
object SafetyCopies {
    const val KEEP = 3

    private const val PREFIX = "safety-"
    private const val SUFFIX = ".db.gz"
    private const val DIR = "restore-safety"

    private val NAME = Regex("^safety-(\\d{8}T\\d{6})\\.db\\.gz$")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun name(at: LocalDateTime): String = "$PREFIX${at.format(STAMP)}$SUFFIX"

    /** @return 写好的 gzip 文件；调用方不需要删它，它就是那份快照。 */
    fun take(db: RoomDatabase, context: Context, at: LocalDateTime): File =
        DbSnapshot.into(db, context, File(dir(context), name(at)))

    /** 本机留着的快照，新到旧。名字里带时刻，字典序即时序。 */
    fun list(context: Context): List<RemoteFile> = snapshots(context).map { RemoteFile(it.name, it.length()) }

    /** 只认这个名字形状，绝不去拼用户给的路径。 */
    fun file(context: Context, name: String): File? =
        if (!NAME.matches(name)) null else File(dir(context), name).takeIf { it.isFile }

    /** 名字里那一瞬间转成一行看得懂的时刻；认不出来给空串，界面自己决定退回报什么。 */
    fun label(name: String): String = NAME.matchEntire(name)
        ?.groupValues?.get(1)
        ?.let { runCatching { LocalDateTime.parse(it, STAMP).format(READABLE) }.getOrNull() }
        .orEmpty()

    fun prune(context: Context) {
        snapshots(context).drop(KEEP).forEach { it.delete() }
    }

    private fun snapshots(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && NAME.matches(f.name) }.orEmpty().sortedByDescending { it.name }
}
