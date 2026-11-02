package com.immerlex.feature.reader

import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.ShortRow
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.ReaderMarks
import com.immerlex.core.schedule.WordAccess
import com.immerlex.core.tricky.TrickyCard

enum class ReaderMode { LEARN, REVIEW }

/** 书架上的一本连载书；[chapters] 为 0 表示刚新建、还没写过正文。[lastReadNumber] 为 0 表示没读过。 */
data class BookOption(
    val genre: String,
    val chapters: Int,
    val lastReadNumber: Int = 0,
    val lastReadSec: Long = 0,
)

/** 刚记下的一条，可以撤销几秒；[word] 让按钮写清楚撤的是哪个词。 */
data class UndoPrompt(val logId: Long, val kind: String, val word: String)

data class ReaderState(
    val loading: Boolean = true,
    val hasChapter: Boolean = false,
    val chapterId: Long = 0,
    val title: String = "",
    val number: Int = 0,
    val kind: String = KIND_SERIAL,
    val book: String = "",
    val books: List<BookOption> = emptyList(),
    val shorts: List<ShortRow> = emptyList(),
    val blocks: List<List<ReaderSegment>> = emptyList(),
    val mode: ReaderMode = ReaderMode.LEARN,
    val chapters: List<ChapterOption> = emptyList(),
    val selected: ReaderWord? = null,
    /** 键盘上下键停在正文第几个词（第 0 个是最前面那个）；空表示还没用键盘选过。 */
    val cursor: Int? = null,
    val access: WordAccess = WordAccess.ViewOnly,
    val previews: List<Pair<Rating, Int>> = emptyList(),
    val bankWords: Map<String, WordEntity> = emptyMap(),
    /** 正文颜色的依据：阅读页自己记过的词，以及今天已经回忆过的那些。 */
    val marks: ReaderMarks = ReaderMarks(),
    /** 取 [marks] 的那一刻，"今天到期"按它判断，不在组合阶段再读一次表。 */
    val marksAtSec: Long = 0,
    val tricky: TrickyCard? = null,
    /** 刚记下、还在撤销窗口里的那一条；按钮就长在词卡里，直到下一个词评分或窗口过掉。 */
    val pendingUndo: UndoPrompt? = null,
    /** 这一张卡刚记过一条：只留撤销，不再给一遍评分按钮。 */
    val graded: Boolean = false,
    val learned: Int = 0,
    val recalled: Int = 0,
    val generating: Boolean = false,
) {
    val wordCount: Int get() = blocks.flatten().count { it is ReaderSegment.Word }
    val isShort: Boolean get() = kind == KIND_SHORT

    /** 正文里的词按出现顺序排成一串，键盘上下键与正文下划线都按这个序号走。 */
    val words: List<ReaderWord>
        get() = blocks.flatten().filterIsInstance<ReaderSegment.Word>().map { it.word }
}

/**
 * 换一章正文：词卡、撤销、当日计数与颜色依据一起作废，颜色等 [marks] 重读回来再点亮。
 * 传进来的 [chapter] 为空表示这一章不存在或正文读坏了，阅读页退回空态。
 */
internal fun ReaderState.showing(
    chapter: ChapterEntity?,
    blocks: List<List<ReaderSegment>>,
    bank: Map<String, WordEntity>,
    chapters: List<ChapterOption>,
    books: List<BookOption>,
    shorts: List<ShortRow>,
    book: String,
): ReaderState = copy(
    loading = false,
    hasChapter = chapter != null,
    chapterId = chapter?.id ?: 0,
    title = chapter?.title.orEmpty(),
    number = chapter?.number ?: 0,
    kind = chapter?.kind ?: KIND_SERIAL,
    book = book,
    books = books,
    shorts = shorts,
    chapters = chapters,
    blocks = blocks,
    bankWords = bank,
    selected = null,
    cursor = null,
    access = WordAccess.ViewOnly,
    previews = emptyList(),
    marks = ReaderMarks(),
    marksAtSec = 0,
    tricky = null,
    pendingUndo = null,
    graded = false,
    learned = 0,
    recalled = 0,
)

/** 键盘上下键的落点：还没选过时上下都落在第一个词，到头就停在头尾，不会绕回另一端。 */
internal fun nextCursor(current: Int?, delta: Int, size: Int): Int? =
    if (size <= 0) null else if (current == null) 0 else (current + delta).coerceIn(0, size - 1)

/** 词卡收起：释义预览与易混卡一起清掉，评分按钮不再亮着。 */
internal fun ReaderState.cardClosed() = copy(
    selected = null,
    access = WordAccess.ViewOnly,
    previews = emptyList(),
    tricky = null,
    graded = false,
)
