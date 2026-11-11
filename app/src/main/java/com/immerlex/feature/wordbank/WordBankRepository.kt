package com.immerlex.feature.wordbank

import androidx.room.withTransaction
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.dict.DictionarySource
import com.immerlex.core.importer.ImportPlan
import com.immerlex.core.importer.ImportRepository
import com.immerlex.core.importer.ImportSummary
import com.immerlex.core.importer.WordListParser
import com.immerlex.core.material.MaterialEntry
import com.immerlex.core.material.MaterialRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Word bank data operations for the UI layer. Imports go through
 * [ImportRepository] so ECDICT enrichment stays in one place.
 */
class WordBankRepository(
    private val db: ImmerlexDatabase,
    private val dictionary: DictionarySource,
    private val imports: ImportRepository,
    private val material: MaterialRepository,
) {
    private val words: WordDao get() = db.wordDao()

    fun observeWords(): Flow<List<WordEntity>> = words.observeAll()

    /** 导出用：整本词库，不受搜索框过滤。 */
    suspend fun allWords(): List<WordEntity> = withContext(Dispatchers.IO) { words.allWords() }

    /**
     * 词典里搜到的、词库还没有的词。已在词库里的不重复列（[exclude] 是词库的全量词面），
     * 所以多取一些再筛，免得词库很大时这一组被稀释成只剩两三条。
     */
    suspend fun dictionarySearch(needle: String, exclude: Set<String>): List<DictEntry> =
        withContext(Dispatchers.IO) {
            dictionary.search(needle, DICTIONARY_LIMIT * 3)
                .filterNot { it.word in exclude }
                .take(DICTIONARY_LIMIT)
        }

    /** 词典卡的素材：这个词进没进词库都有可能有。 */
    suspend fun materialOf(word: String): MaterialEntry? = material.peek(word)

    // First call also copies the 11MB ECDICT asset out of the APK.
    /** 解析 + 补释义 + 找同义词，不写库；同义词由界面问过用户再落盘。 */
    suspend fun prepareImport(text: String): ImportPlan =
        withContext(Dispatchers.IO) { imports.prepare(WordListParser.parse(text)) }

    suspend fun commitImport(plan: ImportPlan, source: String, skipVariants: Boolean): ImportSummary =
        withContext(Dispatchers.IO) { imports.commit(plan, source, skipVariants) }

    /** @return false when the word is blank or already in the bank. */
    suspend fun addManual(word: String, gloss: String): Boolean = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase()
        if (clean.isEmpty() || words.findByWord(clean) != null) {
            false
        } else {
            val entry = dictionary.lookup(clean)
            words.insert(
                WordEntity(
                    word = clean,
                    phonetic = entry?.phonetic.orEmpty(),
                    translation = gloss.trim().ifEmpty { entry?.translation.orEmpty() },
                    definition = entry?.definition.orEmpty(),
                    source = SOURCE_MANUAL,
                    createdAtSec = Instant.now().epochSecond,
                ),
            )
            true
        }
    }

    suspend fun delete(wordId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.reviewLogDao().deleteForWord(wordId)
            words.deleteById(wordId)
        }
    }

    companion object {
        const val SOURCE_MANUAL = "manual"

        /** 词典组一次最多列这么多条：它回答"有没有这个词"，不是给人翻页的。 */
        const val DICTIONARY_LIMIT = 20
    }
}
