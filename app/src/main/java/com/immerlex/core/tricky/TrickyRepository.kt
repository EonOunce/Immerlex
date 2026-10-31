package com.immerlex.core.tricky

import androidx.room.withTransaction
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.TrickyLinkEntity
import com.immerlex.core.db.TrickyWordEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictionarySource
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 易错词与易混关联的唯一写入口。关系只存一份有序对，查询时两边都看，
 * 所以从 affect 关联 effect 之后，点 effect 也能看到 affect。
 */
class TrickyRepository(
    private val db: ImmerlexDatabase,
    private val words: WordDao,
    private val dictionary: DictionarySource,
) {
    private val tricky get() = db.trickyDao()

    suspend fun card(raw: String): TrickyCard = withContext(Dispatchers.IO) {
        val key = normalize(raw)
        val row = tricky.byWord(key)
            ?: return@withContext TrickyCard(marked = false, links = emptyList(), suggestions = emptyList())
        val rows = tricky.all()
        val byWord = rows.associateBy { it.word }
        val bank = words.allWords().associateBy { it.word.lowercase() }
        val links = tricky.linksOf(key).map { link ->
            val other = if (link.wordA == key) link.wordB else link.wordA
            wordOf(other, byWord, bank)
        }.sortedBy { it.word }
        val linked = links.mapTo(HashSet()) { it.word }
        val candidates = (rows.map { it.word } + bank.keys).distinct()
        val suggestions = ConfusableSuggestions.suggestionsOf(key, candidates)
            .filterNot { it in linked }
            .map { wordOf(it, byWord, bank) }
        TrickyCard(marked = true, links = links, suggestions = suggestions)
    }

    suspend fun toggle(raw: String, gloss: String, ipa: String): TrickyCard = withContext(Dispatchers.IO) {
        val key = normalize(raw)
        db.withTransaction {
            if (tricky.byWord(key) != null) {
                tricky.deleteLinksOf(key)
                tricky.delete(key)
            } else {
                ensure(key, gloss, ipa)
            }
        }
        card(key)
    }

    suspend fun link(base: TrickyWord, other: TrickyWord): TrickyCard = withContext(Dispatchers.IO) {
        linkWords(normalize(base.word), base.gloss, base.ipa, normalize(other.word), other.gloss, other.ipa)
    }

    /** 手动输入：先看词库，再查离线词典；词典也没有就照样加入，只是释义空着。 */
    suspend fun addManual(base: TrickyWord, typed: String): AddTrickyResult = withContext(Dispatchers.IO) {
        val key = normalize(base.word)
        val other = normalize(typed)
        if (other.isEmpty() || other == key) return@withContext AddTrickyResult(card(key), added = false)
        val alreadyLinked = tricky.linksOf(key).any { (if (it.wordA == key) it.wordB else it.wordA) == other }
        if (alreadyLinked) return@withContext AddTrickyResult(card(key), added = false)
        val bank = words.findByWord(other)
        val entry = if (bank == null) dictionary.lookup(other) else null
        val next = linkWords(
            key,
            base.gloss,
            base.ipa,
            other,
            bank?.translation ?: entry?.translation.orEmpty(),
            bank?.phonetic ?: entry?.phonetic.orEmpty(),
        )
        AddTrickyResult(next, added = true)
    }

    suspend fun unlink(base: String, other: String): TrickyCard = withContext(Dispatchers.IO) {
        val key = normalize(base)
        tricky.deleteLink(key, normalize(other))
        card(key)
    }

    /** 打开一个易混词时用它拼词卡：不在当前正文、甚至不在词库里的词也能看。 */
    suspend fun detail(raw: String): TrickyDetail = withContext(Dispatchers.IO) {
        val key = normalize(raw)
        val row = tricky.byWord(key)
        val bank = words.findByWord(key)
        val entry = if (row == null && bank == null) dictionary.lookup(key) else null
        TrickyDetail(
            word = key,
            gloss = row?.gloss?.takeIf { it.isNotBlank() }
                ?: bank?.translation
                ?: entry?.translation.orEmpty(),
            ipa = row?.ipa?.takeIf { it.isNotBlank() }
                ?: bank?.phonetic
                ?: entry?.phonetic.orEmpty(),
            definition = bank?.definition ?: entry?.definition.orEmpty(),
            wordId = bank?.id,
        )
    }

    /** 管理页的分组视图：关系图折成连通分量，没挂上任何词的易错词也单独成组。 */
    suspend fun groups(): List<TrickyGroup> = withContext(Dispatchers.IO) {
        val rows = tricky.all().associateBy { it.word }
        val bank = words.allWords().associateBy { it.word.lowercase() }
        TrickyGroups.components(rows.keys.toList(), tricky.allLinks().map { it.wordA to it.wordB })
            .map { members -> TrickyGroup(members.first(), members.map { wordOf(it, rows, bank) }) }
    }

    /** 批量新增：一行一组，组内两两挂上。返回真正建起来的组数。 */
    suspend fun addGroups(text: String): Int = withContext(Dispatchers.IO) {
        val parsed = TrickyGroups.parse(text)
        if (parsed.isEmpty()) return@withContext 0
        db.withTransaction {
            parsed.forEach { group ->
                group.forEach { ensureWithGloss(it) }
                TrickyGroups.pairsOf(group).forEach { (first, second) -> insertLink(first, second) }
            }
        }
        parsed.size
    }

    /** 往一组里加词：跟组里每个词都挂上，所以点哪一个都能看到新来的这个。 */
    suspend fun addToGroup(group: TrickyGroup, typed: String): Boolean = withContext(Dispatchers.IO) {
        val other = normalize(typed)
        if (other.isEmpty() || other in group.words) return@withContext false
        db.withTransaction {
            ensureWithGloss(other)
            group.words.forEach { member ->
                ensureWithGloss(member)
                insertLink(member, other)
            }
        }
        true
    }

    /** 把词从组里移出去：只断关系，易错标记留着。 */
    suspend fun removeFromGroup(group: TrickyGroup, word: String) = withContext(Dispatchers.IO) {
        val key = normalize(word)
        db.withTransaction {
            group.words.filterNot { it == key }.forEach { tricky.deleteLink(key, it) }
        }
    }

    /** 落单的词整条删掉：只动易错这两张表，不碰复习记录。 */
    suspend fun deleteWord(word: String) = withContext(Dispatchers.IO) {
        val key = normalize(word)
        db.withTransaction {
            tricky.deleteLinksOf(key)
            tricky.delete(key)
        }
    }

    private suspend fun linkWords(
        base: String,
        baseGloss: String,
        baseIpa: String,
        other: String,
        otherGloss: String,
        otherIpa: String,
    ): TrickyCard {
        db.withTransaction {
            ensure(base, baseGloss, baseIpa)
            ensure(other, otherGloss, otherIpa)
            insertLink(base, other)
        }
        return card(base)
    }

    private suspend fun insertLink(first: String, second: String) {
        val (a, b) = pairOf(first, second)
        tricky.insertLink(TrickyLinkEntity(a, b, Instant.now().epochSecond))
    }

    /** 用户手打的词没有现成释义：先取词库，再查离线词典，查不到也照样进组。 */
    private suspend fun ensureWithGloss(word: String) {
        val bank = words.findByWord(word)
        val entry = if (bank == null) dictionary.lookup(word) else null
        ensure(word, bank?.translation ?: entry?.translation.orEmpty(), bank?.phonetic ?: entry?.phonetic.orEmpty())
    }

    private suspend fun ensure(word: String, gloss: String, ipa: String) {
        tricky.insertIgnore(TrickyWordEntity(word, gloss.trim(), ipa.trim(), Instant.now().epochSecond))
        if (gloss.isNotBlank() || ipa.isNotBlank()) tricky.fillBlanks(word, gloss.trim(), ipa.trim())
    }

    private fun wordOf(
        word: String,
        trickyRows: Map<String, TrickyWordEntity>,
        bank: Map<String, WordEntity>,
    ): TrickyWord {
        trickyRows[word]?.let { return TrickyWord(it.word, it.gloss, it.ipa) }
        bank[word]?.let { return TrickyWord(word, it.translation, it.phonetic) }
        val entry = dictionary.lookup(word)
        return TrickyWord(word, entry?.translation.orEmpty(), entry?.phonetic.orEmpty())
    }

    private fun normalize(word: String): String = TrickyGroups.normalize(word)

    private fun pairOf(first: String, second: String): Pair<String, String> =
        TrickyGroups.pairOf(first, second)
}
