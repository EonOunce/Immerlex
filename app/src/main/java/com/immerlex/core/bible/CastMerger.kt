package com.immerlex.core.bible

import com.immerlex.core.db.CharacterStateEntity
import java.time.Instant

/**
 * 把"本章登场人物 + 本章变化"合并进人物状态表。纯函数，只算出新行与要改的行，写库归仓库，
 * 这样合并规则能脱离 Android 单测（AGENTS.md §3）。
 *
 * 两条不可让的规则：用户手写的状态永不被覆盖，只留一条冲突提示；比已有记录更旧的章号
 * 不改状态——否则重写第 3 章会让第 5 章死掉的人复活。
 */
object CastMerger {
    data class Outcome(val inserts: List<CharacterStateEntity>, val updates: List<CharacterStateEntity>)

    fun merge(
        genre: String,
        current: List<CharacterStateEntity>,
        appeared: List<String>,
        changes: List<CharacterChange>,
        chapterNumber: Int,
        now: Instant = Instant.now(),
    ): Outcome {
        val index = LinkedHashMap<String, CharacterStateEntity>()
        current.forEach { index[normalizeCastName(it.name)] = it }
        val inserts = LinkedHashMap<String, CharacterStateEntity>()
        val updates = LinkedHashMap<Long, CharacterStateEntity>()
        val nowSec = now.epochSecond

        fun stage(row: CharacterStateEntity) {
            val key = normalizeCastName(row.name)
            index[key] = row
            // 新人物按名字合流：同一章里既登场又有变化时只能有一行，否则写库撞唯一索引。
            if (row.id == 0L) inserts[key] = row else updates[row.id] = row
        }

        fun mention(row: CharacterStateEntity) {
            if (row.lastChapterNumber < chapterNumber) {
                stage(row.copy(lastChapterNumber = chapterNumber, updatedAtSec = nowSec))
            }
        }

        appeared.forEach { token ->
            val (name, role) = splitCastToken(token)
            if (name.isBlank()) return@forEach
            val existing = index[name]
            if (existing == null) {
                stage(
                    CharacterStateEntity(
                        genre = genre, name = name, role = role,
                        firstChapterNumber = chapterNumber, lastChapterNumber = chapterNumber,
                        updatedAtSec = nowSec,
                    ),
                )
                return@forEach
            }
            if (role.isNotBlank() && existing.role.isBlank()) {
                stage(existing.copy(role = role, updatedAtSec = nowSec))
            }
            mention(index.getValue(name))
        }

        changes.forEach { change ->
            val what = change.what.trim()
            val (name, role) = splitCastToken(change.who)
            if (name.isBlank() || what.isBlank()) return@forEach
            val existing = index[name]
            if (existing == null) {
                stage(
                    CharacterStateEntity(
                        genre = genre, name = name, role = role, status = what,
                        firstChapterNumber = chapterNumber, lastChapterNumber = chapterNumber,
                        updatedAtSec = nowSec,
                    ),
                )
                return@forEach
            }
            when {
                existing.status == what -> if (existing.conflictChapterNumber != 0) {
                    stage(existing.copy(conflictChapterNumber = 0, conflictText = "", updatedAtSec = nowSec))
                }

                // 重写旧章不该把后面的剧情抹掉。
                chapterNumber < existing.lastChapterNumber -> Unit

                existing.authoredByUser -> stage(
                    existing.copy(
                        conflictChapterNumber = chapterNumber, conflictText = what,
                        lastChapterNumber = maxOf(existing.lastChapterNumber, chapterNumber),
                        updatedAtSec = nowSec,
                    ),
                )

                else -> stage(
                    existing.copy(
                        status = what, conflictChapterNumber = 0, conflictText = "",
                        lastChapterNumber = maxOf(existing.lastChapterNumber, chapterNumber),
                        updatedAtSec = nowSec,
                    ),
                )
            }
        }
        return Outcome(inserts.values.toList(), updates.values.toList())
    }
}
