package com.immerlex.core.material

import androidx.room.withTransaction
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.dict.DictionarySource
import com.immerlex.core.word.wordSurface
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一次导入的两种下场。整份文件不成形状是 [Rejected]（不能说成"这个包是空的"），
 * 收下之后写进去多少行、每种理由各丢了几条，都由 [Imported] 报出来。
 */
sealed interface ImportOutcome {
    data class Rejected(val failure: PackFailure) : ImportOutcome

    /**
     * [written] 只数内容真的变了的行；[dropped] 按理由分行数；
     * [badCollocations] 是那些不含该词、被丢掉的短语条数（搭配的错法和整行的错法不是一回事）。
     */
    data class Imported(
        val written: Int,
        val dropped: Map<DropReason, Int>,
        val badCollocations: Int = 0,
    ) : ImportOutcome {
        fun droppedTotal(): Int = dropped.values.sum()
    }
}

/**
 * 出题素材的进出这一扇门。库里一行按小写词面存（Room v15），所以包里的考研词
 * 不必先进词库：它以后进不进、什么时候进，素材都还认得自己。
 *
 * 一次导入是一个事务：中途崩了就当没导过，不会出现"半个包"那种最难查的状态。
 */
class MaterialRepository(
    private val db: ImmerlexDatabase,
    private val dictionary: DictionarySource,
) {
    private val material get() = db.quizMaterialDao()

    suspend fun count(): Int = material.count()

    /** 整表倒出来，拿到另一台机器就能原样导入。 */
    suspend fun export(): String = MaterialPack.render(material.all())

    suspend fun import(text: String): ImportOutcome = withContext(Dispatchers.IO) {
        val parsed = MaterialPack.parse(text)
        val failure = parsed.failure
        if (failure != null) return@withContext ImportOutcome.Rejected(failure)
        val entries = parsed.read.entries
        if (entries.isEmpty()) {
            return@withContext ImportOutcome.Imported(0, parsed.read.dropped, parsed.read.badCollocations)
        }
        val atSec = Instant.now().epochSecond
        val changed = MaterialMerge.plan(entries, current(entries), atSec) {
            dictionary.lookup(it)?.translation.orEmpty()
        }
        db.withTransaction { changed.chunked(WRITE_CHUNK).forEach { material.upsertAll(it) } }
        ImportOutcome.Imported(changed.size, parsed.read.dropped, parsed.read.badCollocations)
    }

    /** 词典卡问的是"这个词已经有什么素材"。没有那一行就回 null，界面不编。 */
    suspend fun peek(word: String): MaterialEntry? = withContext(Dispatchers.IO) {
        material.byWords(listOf(wordSurface(word))).firstOrNull()?.let(MaterialPack::entryOf)
    }

    private suspend fun current(entries: List<MaterialEntry>) =
        entries.chunked(READ_CHUNK)
            .flatMap { chunk -> material.byWords(chunk.map { it.word }) }
            .associateBy { it.word }

    private companion object {
        // IN 的参数量与一条语句里的绑定值都有上限，几千行的包必须切着问、切着写。
        const val READ_CHUNK = 400
        const val WRITE_CHUNK = 100
    }
}
