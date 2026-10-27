package com.immerlex.testsupport

import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.ReviewStats
import com.immerlex.core.db.RatingCount
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.dict.DictionarySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeWordDao : WordDao {
    val items = mutableMapOf<Long, WordEntity>()
    private var nextId = 1L

    override suspend fun findByWord(word: String) = items.values.find { it.word == word }
    override suspend fun findByWords(words: List<String>) = items.values.filter { it.word in words }
    override suspend fun findById(id: Long) = items[id]
    override suspend fun insert(entity: WordEntity): Long {
        if (items.values.any { it.word == entity.word }) return -1
        val id = nextId++
        items[id] = entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: WordEntity) { items[entity.id] = entity }
    override suspend fun deleteById(id: Long) { items.remove(id) }
    override fun observeAll(): Flow<List<WordEntity>> = flowOf(items.values.sortedBy { it.word })
    override suspend fun search(query: String, limit: Int) =
        items.values.filter { it.word.contains(query) }.take(limit)

    override suspend fun totalCount() = items.size
    override suspend fun newCount() = items.values.count { it.stability == null }
    override suspend fun dueWords(nowSec: Long, limit: Int) =
        items.values.filter { it.dueEpochSec == null || it.dueEpochSec!! <= nowSec }.take(limit)

    override suspend fun dueCount(nowSec: Long) =
        items.values.count { it.dueEpochSec == null || it.dueEpochSec!! <= nowSec }

    override suspend fun upcomingReviews(limit: Int) =
        items.values.filter { it.stability != null }.take(limit)
}

internal class FakeReviewLogDao : ReviewLogDao {
    val items = mutableListOf<ReviewLogEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: ReviewLogEntity): Long {
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun forWord(wordId: Long) = items.filter { it.wordId == wordId }
    override suspend fun statsSince(sinceSec: Long) =
        ReviewStats(items.size, items.count { it.rating >= 3 })

    override suspend fun ratingHistogram(sinceSec: Long) =
        items.groupingBy { it.rating }.eachCount().map { RatingCount(it.key, it.value) }

    override suspend fun deleteForWord(wordId: Long) { items.removeAll { it.wordId == wordId } }
}

internal class FakeDictionary(private val entries: Map<String, DictEntry> = emptyMap()) : DictionarySource {
    override fun lookup(word: String): DictEntry? = entries[word.lowercase()]

    companion object {
        fun entry(word: String, translation: String, phonetic: String = "", definition: String = "") =
            word.lowercase() to DictEntry(word, phonetic, translation, definition)
    }
}
