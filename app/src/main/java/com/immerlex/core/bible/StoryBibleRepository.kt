package com.immerlex.core.bible

import com.immerlex.core.db.BibleEntryDao
import com.immerlex.core.db.BibleEntryEntity
import com.immerlex.core.db.CharacterStateDao
import com.immerlex.core.db.CharacterStateEntity
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.StoryBibleDao
import com.immerlex.core.db.StoryBibleEntity
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 故事档案的读写入口：prompt 要的三层上下文、开局展开落库、逐条编辑、每章"本章变化"的合并
 * 都在这里，feature 只调它（AGENTS.md §3：跨 feature 共享的逻辑下沉 core）。
 */
class StoryBibleRepository(
    private val bibleDao: StoryBibleDao,
    private val entryDao: BibleEntryDao,
    private val castDao: CharacterStateDao,
    private val chapterDao: ChapterDao,
) : StoryContextSource {

    override suspend fun contextFor(genre: String, excludeChapterId: Long): StoryContext {
        adoptLegacyOnce()
        return StoryContext(
            entries = sortedEntries(genre).mapNotNull { entity -> sectionOf(entity.section)?.let { BibleLine(it, entity.text, entity.authoredByUser) } },
            cast = castDao.forGenre(genre).map { it.toCastLine() },
            // 全书每章一行，由旧到新：ADR-0005 算过 60 章也就 4–5 千字，不值得为省它丢剧情。
            recaps = chapterDao.recaps(genre, excludeChapterId)
                .filter { it.summary.isNotBlank() }
                .map { RecapLine(it.number, it.title, it.summary) },
        )
    }

    override suspend fun lastChapterNumber(genre: String): Int = bibleDao.forGenre(genre)?.lastChapterNumber ?: 0

    override suspend fun recordChapterOutcome(
        genre: String,
        chapterNumber: Int,
        appeared: List<String>,
        changes: List<CharacterChange>,
        now: Instant,
    ) {
        adoptLegacyOnce()
        val outcome = CastMerger.merge(genre, castDao.forGenre(genre), appeared, changes, chapterNumber, now)
        outcome.inserts.forEach { castDao.insert(it) }
        outcome.updates.forEach { castDao.update(it) }
        val bible = bibleDao.forGenre(genre) ?: blankBible(genre, now)
        bibleDao.upsert(
            bible.copy(
                lastChapterNumber = maxOf(bible.lastChapterNumber, chapterNumber),
                updatedAtSec = now.epochSecond,
            ),
        )
    }

    // ---- 故事档案面板读写 ----

    suspend fun seedOf(genre: String): String = bibleDao.forGenre(genre)?.seedText.orEmpty()

    suspend fun entriesOf(genre: String): List<BibleEntryEntity> {
        adoptLegacyOnce()
        return sortedEntries(genre)
    }

    suspend fun castOf(genre: String): List<CharacterStateEntity> {
        adoptLegacyOnce()
        return castDao.forGenre(genre)
    }

    suspend fun saveSeed(genre: String, text: String, now: Instant = Instant.now()) {
        val bible = bibleDao.forGenre(genre) ?: blankBible(genre, now)
        bibleDao.upsert(bible.copy(seedText = text.trim(), updatedAtSec = now.epochSecond))
    }

    /** 展开或重掷：模型生成的条目整批换掉，用户手写的一律留着。 */
    suspend fun applyDraft(genre: String, draft: BibleDraft, now: Instant = Instant.now()) {
        adoptLegacyOnce()
        entryDao.deleteGenerated(genre)
        val authored = entryDao.forGenre(genre).filter { it.authoredByUser }
        val rows = draft.sections()
            .groupBy({ it.first }, { it.second })
            .flatMap { (section, texts) ->
                // 单栏区（基调/主角/结局）只容一句话：读者既然自己写过，重掷就不再补一句模型的，
                // 否则 prompt 里会出现两条互相打架的设定，模型只能挑一条听。
                val kept = if (section in SINGLE_LINE_SECTIONS && authored.any { it.section == section.name }) {
                    emptyList()
                } else {
                    texts.filter { text -> authored.none { it.section == section.name && it.text == text } }
                }
                val base = authored.count { it.section == section.name }
                kept.mapIndexed { index, text ->
                    BibleEntryEntity(
                        genre = genre, section = section.name, text = text,
                        ordinal = base + index, updatedAtSec = now.epochSecond,
                    )
                }
            }
        if (rows.isNotEmpty()) entryDao.insertAll(rows)
        // 起始人物只补新面孔：老角色的状态是后面几十章累积出来的，重掷档案不该把它抹掉。
        val seen = castDao.forGenre(genre).map { normalizeCastName(it.name) }.toMutableSet()
        draft.cast.forEach { seed ->
            val name = normalizeCastName(seed.name)
            if (name.isNotBlank() && seen.add(name)) {
                castDao.insert(
                    CharacterStateEntity(
                        genre = genre, name = name, role = seed.role.trim(), status = seed.start.trim(),
                        firstChapterNumber = 0, lastChapterNumber = 0, updatedAtSec = now.epochSecond,
                    ),
                )
            }
        }
    }

    /** 用户动过笔的条目从此有最高优先级，冲突提示同时清掉（他既然改了，就按他说的写）。 */
    suspend fun saveEntry(id: Long, text: String, now: Instant = Instant.now()) {
        entryDao.byId(id)?.let {
            entryDao.update(it.copy(text = text.trim(), authoredByUser = true, updatedAtSec = now.epochSecond))
        }
    }

    suspend fun addEntry(genre: String, section: BibleSection, text: String, now: Instant = Instant.now()) {
        if (text.isBlank()) return
        val ordinal = entryDao.forGenre(genre).count { sectionOf(it.section) == section }
        entryDao.insert(
            BibleEntryEntity(
                genre = genre, section = section.name, text = text.trim(),
                ordinal = ordinal, authoredByUser = true, updatedAtSec = now.epochSecond,
            ),
        )
    }

    suspend fun deleteEntry(id: Long) = entryDao.deleteById(id)

    suspend fun saveCast(id: Long, name: String, role: String, status: String, now: Instant = Instant.now()) {
        castDao.byId(id)?.let {
            castDao.update(
                it.copy(
                    name = name.trim().ifBlank { it.name }, role = role.trim(), status = status.trim(),
                    authoredByUser = true, conflictChapterNumber = 0, conflictText = "",
                    updatedAtSec = now.epochSecond,
                ),
            )
        }
    }

    /** 手动加一个人物；重名（按 [normalizeCastName] 判）时返回 false，表上有唯一索引兜底。 */
    suspend fun addCast(genre: String, name: String, role: String, status: String, now: Instant = Instant.now()): Boolean {
        val clean = normalizeCastName(name)
        if (clean.isBlank()) return false
        if (castDao.forGenre(genre).any { normalizeCastName(it.name) == clean }) return false
        castDao.insert(
            CharacterStateEntity(
                genre = genre, name = clean, role = role.trim(), status = status.trim(),
                authoredByUser = true, updatedAtSec = now.epochSecond,
            ),
        )
        return true
    }

    suspend fun deleteCast(id: Long): String {
        val name = castDao.byId(id)?.name.orEmpty()
        castDao.deleteById(id)
        return name
    }

    // ---- 升级前的单一 synopsis / 12 条角色表 ----

    private val adoptionLock = Mutex()
    @Volatile private var adopted = false

    /**
     * 旧缓存搬一次：角色表进 [CharacterStateEntity]，首末章号留 0（当时并不知道是谁在哪章出现的），
     * 搬完把旧列清空。搬之前没人读新表，所以不会读到半套档案。
     */
    private suspend fun adoptLegacyOnce() {
        if (adopted) return
        adoptionLock.withLock {
            if (adopted) return
            bibleDao.withLegacyCache().forEach { bible ->
                val cached = decodeTokens(bible.charactersJson)
                if (cached.isNotEmpty() && castDao.forGenre(bible.genre).isEmpty()) {
                    val seen = mutableSetOf<String>()
                    cached.forEach { token ->
                        val (name, role) = splitCastToken(token)
                        if (name.isNotBlank() && seen.add(name)) {
                            castDao.insert(
                                CharacterStateEntity(genre = bible.genre, name = name, role = role, updatedAtSec = bible.updatedAtSec),
                            )
                        }
                    }
                }
                bibleDao.upsert(bible.copy(charactersJson = "[]", synopsis = ""))
            }
            adopted = true
        }
    }

    private fun blankBible(genre: String, now: Instant) = StoryBibleEntity(genre = genre, updatedAtSec = now.epochSecond)

    /** 分区按枚举顺序呈现（基调 → 主角与目标 → 结局倾向 → 禁止事项），区内按写入顺序。 */
    private suspend fun sortedEntries(genre: String): List<BibleEntryEntity> =
        entryDao.forGenre(genre).sortedWith(
            compareBy<BibleEntryEntity> { sectionOf(it.section)?.ordinal ?: Int.MAX_VALUE }
                .thenBy { it.ordinal }
                .thenBy { it.id },
        )

    private fun decodeTokens(array: String): List<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), array)
    }.getOrDefault(emptyList()).filter { it.isNotBlank() && it != "[]" }

    private fun BibleDraft.sections(): List<Pair<BibleSection, String>> = buildList {
        if (tone.isNotBlank()) add(BibleSection.TONE to tone.trim())
        if (protagonist.isNotBlank()) add(BibleSection.PROTAGONIST to protagonist.trim())
        if (ending.isNotBlank()) add(BibleSection.ENDING to ending.trim())
        forbidden.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(BibleSection.FORBIDDEN to it) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val SINGLE_LINE_SECTIONS = setOf(BibleSection.TONE, BibleSection.PROTAGONIST, BibleSection.ENDING)
    }
}

fun sectionOf(name: String): BibleSection? = runCatching { BibleSection.valueOf(name) }.getOrNull()
