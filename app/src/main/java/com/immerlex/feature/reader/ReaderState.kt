package com.immerlex.feature.reader

import com.immerlex.core.db.ChapterOption
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.ShortRow
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
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

/** 刚记下的一条，可以撤销几秒；[kind] 决定撤销后把哪个计数退回去。 */
data class UndoPrompt(val logId: Long, val kind: String)

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
    val access: WordAccess = WordAccess.ViewOnly,
    val previews: List<Pair<Rating, Int>> = emptyList(),
    val bankWords: Map<String, WordEntity> = emptyMap(),
    val tricky: TrickyCard? = null,
    val learned: Int = 0,
    val recalled: Int = 0,
    val generating: Boolean = false,
) {
    val wordCount: Int get() = blocks.flatten().count { it is ReaderSegment.Word }
    val isShort: Boolean get() = kind == KIND_SHORT
}
