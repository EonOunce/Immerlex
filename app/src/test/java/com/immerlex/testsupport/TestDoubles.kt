package com.immerlex.testsupport

import com.immerlex.core.db.BibleEntryDao
import com.immerlex.core.db.BibleEntryEntity
import com.immerlex.core.db.CharacterStateDao
import com.immerlex.core.db.CharacterStateEntity
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.db.ChapterRecap
import com.immerlex.core.db.DailyLoopDao
import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.ReviewMoment
import com.immerlex.core.db.ReviewStats
import com.immerlex.core.db.RatingCount
import com.immerlex.core.db.StoryBibleDao
import com.immerlex.core.db.StoryBibleEntity
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

    override suspend fun update(entity: ReviewLogEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        check(index >= 0) { "amend targeted a log that is not in the table" }
        items[index] = entity
    }

    override suspend fun statsSince(sinceSec: Long) =
        ReviewStats(items.size, items.count { it.rating >= 3 })

    override suspend fun ratingHistogram(sinceSec: Long) =
        items.groupingBy { it.rating }.eachCount().map { RatingCount(it.key, it.value) }

    override suspend fun reviewsSince(sinceSec: Long) = items
        .filter { it.reviewEpochSec >= sinceSec }
        .map { ReviewMoment(it.wordId, it.reviewEpochSec) }

    override suspend fun deleteForWord(wordId: Long) { items.removeAll { it.wordId == wordId } }
}

internal class FakeDailyLoopDao : DailyLoopDao {
    val items = mutableMapOf<Long, DailyLoopEntity>()

    override suspend fun upsert(entity: DailyLoopEntity) { items[entity.epochDay] = entity }

    override suspend fun byDay(epochDay: Long) = items[epochDay]

    override suspend fun since(fromDay: Long) = items.values.filter { it.epochDay >= fromDay }.sortedBy { it.epochDay }
}

internal class FakeDictionary(private val entries: Map<String, DictEntry> = emptyMap()) : DictionarySource {
    override fun lookup(word: String): DictEntry? = entries[word.lowercase()]

    companion object {
        fun entry(word: String, translation: String, phonetic: String = "", definition: String = "") =
            word.lowercase() to DictEntry(word, phonetic, translation, definition)
    }
}

internal class FakeChapterDao : ChapterDao {
    val items = mutableListOf<ChapterEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: ChapterEntity): Long {
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: ChapterEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        check(index >= 0) { "rewrite targeted a chapter the table does not have" }
        items[index] = entity
    }

    override suspend fun byId(id: Long) = items.find { it.id == id }

    override suspend fun titlesForGenre(genre: String) = items.filter { it.genre == genre }
        .sortedWith(compareByDescending<ChapterEntity> { it.number }.thenByDescending { it.id })
        .map { ChapterOption(it.id, it.number, it.title) }

    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }

    override suspend fun latest(genre: String) = items.filter { it.genre == genre }.maxByOrNull { it.number }

    override suspend fun recaps(genre: String, excludeId: Long) = items
        .filter { it.genre == genre && it.id != excludeId }
        .sortedWith(compareBy({ it.number }, { it.id }))
        .map { ChapterRecap(it.number, it.title, it.summary) }
}

internal class FakeStoryBibleDao : StoryBibleDao {
    val items = mutableMapOf<String, StoryBibleEntity>()
    private var nextId = 1L

    override suspend fun forGenre(genre: String) = items[genre]

    override suspend fun upsert(entity: StoryBibleEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items[entity.genre] = entity.copy(id = id)
        return id
    }

    override suspend fun withLegacyCache() = items.values
        .filter { it.charactersJson != "[]" || it.synopsis.isNotBlank() }
        .sortedBy { it.genre }
}

internal class FakeBibleEntryDao : BibleEntryDao {
    val items = mutableListOf<BibleEntryEntity>()
    private var nextId = 1L

    override suspend fun forGenre(genre: String) = items.filter { it.genre == genre }
        .sortedWith(compareBy({ it.ordinal }, { it.id }))

    override suspend fun byId(id: Long) = items.find { it.id == id }

    override suspend fun insert(entity: BibleEntryEntity): Long {
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<BibleEntryEntity>) { entities.forEach { insert(it) } }

    override suspend fun update(entity: BibleEntryEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        check(index >= 0) { "edit targeted an entry the table does not have" }
        items[index] = entity
    }

    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }

    override suspend fun deleteGenerated(genre: String) {
        items.removeAll { it.genre == genre && !it.authoredByUser }
    }
}

/** 与真表一样拒绝同名人物：(genre, name) 上有唯一索引。 */
internal class FakeCharacterStateDao : CharacterStateDao {
    val items = mutableListOf<CharacterStateEntity>()
    private var nextId = 1L

    override suspend fun forGenre(genre: String) = items.filter { it.genre == genre }
        .sortedWith(compareBy({ it.firstChapterNumber }, { it.id }))

    override suspend fun byId(id: Long) = items.find { it.id == id }

    override suspend fun insert(entity: CharacterStateEntity): Long {
        check(items.none { it.genre == entity.genre && it.name == entity.name }) {
            "UNIQUE constraint: character_states(${entity.genre}, ${entity.name})"
        }
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: CharacterStateEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        check(index >= 0) { "merge updated a person the table does not have" }
        items[index] = entity
    }

    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }
}
