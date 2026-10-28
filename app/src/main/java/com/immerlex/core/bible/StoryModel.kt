package com.immerlex.core.bible

import com.immerlex.core.db.CharacterStateEntity
import java.time.Instant

/** 故事档案的分区；顺序即面板与 prompt 里的呈现顺序。 */
enum class BibleSection { TONE, PROTAGONIST, ENDING, FORBIDDEN }

/** One earlier chapter, oldest first, so the model continues instead of restarting. */
data class RecapLine(val number: Int, val title: String, val summary: String)

data class BibleLine(
    val section: BibleSection,
    val text: String,
    val authoredByUser: Boolean,
)

data class CastLine(
    val name: String,
    val role: String,
    val status: String,
    /** 用户手写状态与剧情对不上时的章号，0 = 没有冲突。 */
    val conflictChapter: Int = 0,
    val conflictText: String = "",
    val authoredByUser: Boolean = false,
)

/** 三层上下文（docs/adr/0005）：档案条目、人物当前状态、全书逐章梗概。 */
data class StoryContext(
    val entries: List<BibleLine> = emptyList(),
    val cast: List<CastLine> = emptyList(),
    val recaps: List<RecapLine> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty() && cast.isEmpty() && recaps.isEmpty()
}

/** 模型在章节 JSON 里回报的一条"本章变化"。 */
data class CharacterChange(val who: String, val what: String)

/** 开局那一段话展开出来的档案草稿。 */
data class BibleDraft(
    val tone: String = "",
    val protagonist: String = "",
    val ending: String = "",
    val forbidden: List<String> = emptyList(),
    val cast: List<CastSeed> = emptyList(),
)

data class CastSeed(val name: String, val role: String = "", val start: String = "")

/** Prompt 侧的连贯层来源；章节生成不认识 Room。 */
interface StoryContextSource {
    /** [excludeChapterId] keeps a rewritten chapter out of its own recap; pass 0 for a new chapter. */
    suspend fun contextFor(genre: String, excludeChapterId: Long): StoryContext

    /** 上一本的章号，删掉最后一章也不回退，所以新生成的章不会撞号。 */
    suspend fun lastChapterNumber(genre: String): Int

    suspend fun recordChapterOutcome(
        genre: String,
        chapterNumber: Int,
        appeared: List<String>,
        changes: List<CharacterChange>,
        now: Instant = Instant.now(),
    )
}

const val CAST_SEPARATOR = " · "

/** "落薇 · 皇后" → ("落薇", "皇后")；没有身份段时身份为空。 */
fun splitCastToken(token: String): Pair<String, String> {
    val trimmed = token.trim()
    val cut = trimmed.indexOf(CAST_SEPARATOR)
    return if (cut < 0) {
        splitBraces(trimmed)
    } else {
        val name = normalizeCastName(trimmed.substring(0, cut))
        val rest = trimmed.substring(cut + CAST_SEPARATOR.length).trim()
        name to splitBraces(rest).first
    }
}

fun castToken(name: String, role: String): String =
    if (role.isBlank()) name else "$name$CAST_SEPARATOR$role"

/**
 * 模型回报人名时可能写成"落薇（皇后）""落薇，皇帝之妻"，必须先折成同一个人才能合并状态。
 * 只去掉身份修饰，人名本身的空格保留。
 */
fun normalizeCastName(raw: String): String =
    raw.split('·', '（', '(', '，', ',', '、', ':', '：').firstOrNull().orEmpty().trim()

private fun splitBraces(text: String): Pair<String, String> {
    val open = text.indexOfFirst { it == '（' || it == '(' }
    return if (open < 0) normalizeCastName(text) to ""
    else normalizeCastName(text.substring(0, open)) to text.substring(open + 1).removeSuffix("）").removeSuffix(")").trim()
}

/** 把状态表里的一个人换成 prompt 行。 */
fun CharacterStateEntity.toCastLine() = CastLine(
    name = name,
    role = role,
    status = status,
    conflictChapter = conflictChapterNumber,
    conflictText = conflictText,
    authoredByUser = authoredByUser,
)
