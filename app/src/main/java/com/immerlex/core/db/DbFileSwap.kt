package com.immerlex.core.db

import java.io.File
import java.io.IOException

/**
 * 换库不是"删了再复制"：新库先在旁边写好，旧库整套改名成 `.prev`，
 * 副本就位后才删 `.prev`。中途失败就把旧库原样放回；进程恰好被杀，
 * 下次开库前由 [recover] 把没换完的现场恢复成旧库。
 */
object DbFileSwap {
    private val SIDECARS = listOf("", "-wal", "-shm")
    private const val PREV = ".prev"
    private const val STAGING = ".new"

    fun install(target: File, replacement: File) {
        target.parentFile?.mkdirs()
        val staging = File(target.path + STAGING)
        staging.delete()
        try {
            replacement.copyTo(staging, overwrite = true)
            if (staging.length() != replacement.length()) throw IOException("short copy")
        } catch (t: Throwable) {
            staging.delete()
            throw t
        }

        recover(target)
        try {
            SIDECARS.drop(1).forEach { moveAside(target, it) }
            moveAside(target, "")
            if (!staging.renameTo(target)) throw IOException("cannot move new database into place")
        } catch (t: Throwable) {
            restore(target)
            staging.delete()
            throw t
        }
        // 旧库的 WAL 属于旧库，换库成功后一律丢掉；留着会把旧页盖到新库上。
        SIDECARS.forEach { File(target.path + it + PREV).delete() }
    }

    fun recover(target: File) {
        // 目标不在而 .prev 在：上次换库在上半场被杀，旧库还没让位完成。
        if (!target.exists()) restore(target)
        SIDECARS.forEach { File(target.path + it + PREV).delete() }
    }

    private fun moveAside(target: File, suffix: String) {
        val old = File(target.path + suffix)
        if (!old.exists()) return
        if (!old.renameTo(File(target.path + suffix + PREV))) throw IOException("cannot move aside $suffix")
    }

    private fun restore(target: File) {
        if (!File(target.path + PREV).exists()) return
        SIDECARS.drop(1).forEach { File(target.path + it).delete() }
        File(target.path).delete()
        SIDECARS.forEach { suffix ->
            val backup = File(target.path + suffix + PREV)
            if (backup.exists()) backup.renameTo(File(target.path + suffix))
        }
    }
}
