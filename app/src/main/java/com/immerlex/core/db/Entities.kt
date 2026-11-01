package com.immerlex.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "words", indices = [Index(value = ["word"], unique = true)])
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val phonetic: String = "",
    val translation: String = "",
    val definition: String = "",
    val source: String,
    val createdAtSec: Long,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueEpochSec: Long? = null,
    val lastReviewEpochSec: Long? = null,
    val fsrsState: Int = FSRS_STATE_LEARNING,
    val reps: Int = 0,
    val lapses: Int = 0,
    val intervalDays: Int = 0,
) {
    val isNew: Boolean get() = stability == null
}

const val FSRS_STATE_LEARNING = 0
const val FSRS_STATE_REVIEW = 1
const val FSRS_STATE_RELEARNING = 2

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val title: String,
    val genre: String,
    val createdAtSec: Long,
    val segmentsJson: String,
    val summary: String = "",
    /** [KIND_SERIAL] = 连载里的一章，[KIND_SHORT] = 单篇自足的短篇。 */
    @ColumnInfo(defaultValue = KIND_SERIAL) val kind: String = KIND_SERIAL,
    /** 短篇：用户自己写的腔调。空 = 用 [genre] 里那个预设。 */
    @ColumnInfo(defaultValue = "") val toneText: String = "",
    /** 短篇：用户说这篇想写什么。空 = 由模型自己定。 */
    @ColumnInfo(defaultValue = "") val idea: String = "",
)

const val KIND_SERIAL = "serial"
const val KIND_SHORT = "short"

/**
 * 书架记住的阅读位置。key 是书架条目：连载书用 `book:<题材>`（一本一行），
 * 短篇用 `short:<章 id>`。[updatedAtSec] 最大的那行就是"上次在看的东西"，
 * 因此关掉应用再打开会回到同一本、同一章；被删的章由 [com.immerlex.core.db.ChapterDao] 的兜底处理。
 */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val key: String,
    /** 0 = 这本书还没有章节（刚新建）。 */
    val chapterId: Long,
    val updatedAtSec: Long,
)

const val READING_BOOK_PREFIX = "book:"
const val READING_SHORT_PREFIX = "short:"

fun bookReadingKey(genre: String): String = READING_BOOK_PREFIX + genre
fun shortReadingKey(chapterId: Long): String = READING_SHORT_PREFIX + chapterId

@Entity(
    tableName = "review_logs",
    indices = [Index("wordId"), Index("reviewEpochSec")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val rating: Int,
    val source: String,
    val reviewEpochSec: Long,
    val elapsedDays: Int,
    val scheduledDays: Int,
    // 评分前的卡片状态。阅读页的撤销要回到这里、再用同一评分在原时刻重算，
    // 否则会把"撤一次"累积成"多复习一次"。见 docs/adr/0012-recall-events-and-quiz-types.md。
    val preStability: Double? = null,
    val preDifficulty: Double? = null,
    val preState: Int = FSRS_STATE_LEARNING,
    // 0 表示"当时还没有排期"（首次复习）；[PRE_DUE_LEGACY] 表示升级前的老记录，没有快照、不可回滚。
    val preDueEpochSec: Long = PRE_DUE_LEGACY,
    val preLastReviewEpochSec: Long? = null,
    /** [LOG_KIND_LEARN] 或 [LOG_KIND_RECALL]；v6 及更早的行都是真实回忆，默认 recall。 */
    @ColumnInfo(defaultValue = LOG_KIND_RECALL) val kind: String = LOG_KIND_RECALL,
    /** 测验题型名（[com.immerlex.core.quiz.QuizType]），阅读页与老记录为空。 */
    val questionType: String? = null,
) {
    val hasPreRatingSnapshot: Boolean get() = preDueEpochSec != PRE_DUE_LEGACY
    val isLearn: Boolean get() = kind == LOG_KIND_LEARN
}

/** v1 rows carry no snapshot; ALTER TABLE backfills them with this sentinel. */
const val PRE_DUE_LEGACY = -1L

/** 第一次看到词、打开释义：只建立排期，不算一次回忆成功。 */
const val LOG_KIND_LEARN = "learn"

/** 先回忆再反馈的一次记录；阅读复习、测验、提前复习都记这个。 */
const val LOG_KIND_RECALL = "recall"

/** 阅读页写下的记录。正文里的颜色只听这一类：测验和提前复习排的是同一个词，但不改颜色。 */
const val LOG_SOURCE_READER = "reader"

/** 测验与提前复习写下的记录；分享卡的"今天测验过的词"只认这一类。 */
const val LOG_SOURCE_QUIZ = "quiz"

@Entity(tableName = "story_bibles", indices = [Index(value = ["genre"], unique = true)])
data class StoryBibleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    // Superseded by bible_entries + character_states + per-chapter summaries, but kept as columns:
    // ALTER TABLE DROP COLUMN is not reliable on minSdk 26, so the old cache is adopted once into
    // the new tables and then left blank rather than dropped.
    val charactersJson: String = "[]",
    val synopsis: String = "",
    val seedText: String = "",
    val lastChapterNumber: Int = 0,
    val updatedAtSec: Long,
)

/**
 * One line of the story bible. [authoredByUser] marks lines the user wrote or edited by hand —
 * automatic merging must never overwrite them, only flag a conflict (docs/adr/0005).
 */
@Entity(tableName = "bible_entries")
data class BibleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    /** [com.immerlex.core.bible.BibleSection] name. */
    val section: String,
    val text: String,
    val ordinal: Int,
    val authoredByUser: Boolean = false,
    val updatedAtSec: Long,
)

/** 人物状态表：一个人一行，[status] 是最新已知状态，由模型回报的"本章变化"合并而来。 */
@Entity(
    tableName = "character_states",
    indices = [Index(value = ["genre", "name"], unique = true)],
)
data class CharacterStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    val name: String,
    val role: String = "",
    val status: String = "",
    val firstChapterNumber: Int = 0,
    val lastChapterNumber: Int = 0,
    val authoredByUser: Boolean = false,
    /** 用户手写条目与最新剧情对不上时写在这里；状态本身不动。0 = 没有冲突。 */
    val conflictChapterNumber: Int = 0,
    val conflictText: String = "",
    val updatedAtSec: Long,
)

/**
 * 一天一行，只存两件**过后就查不到**的事实：那天的到期词清空了、那天用了本周唯一一次补卡。
 * "那天有没有打卡"不存——它已经在 `review_logs` 里，再抄一份就会有两套说法。见 docs/adr/0006 第 2 条。
 */
@Entity(tableName = "daily_loop")
data class DailyLoopEntity(
    /** 本地日历日序号（`LocalDate.toEpochDay()`）。 */
    @PrimaryKey val epochDay: Long,
    val dueCleared: Boolean = false,
    val makeupConsumed: Boolean = false,
)

/** 每次备份留一行，设置页的"上次备份：…/失败：…"就是最新那条。 */
@Entity(tableName = "backup_runs", indices = [Index("atSec")])
data class BackupRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atSec: Long,
    val ok: Boolean,
    val remoteName: String = "",
    val bytes: Long = 0,
    /** 失败时是给人看的短句（"账号或密码不对"），不是堆栈。 */
    val detail: String = "",
)

/**
 * 用户手动标出的易错词。不挂 `words` 外键：阅读页里没入库的词也要能标。
 * [word] 统一小写，[gloss] 可能来自阅读上下文、词库或离线词典，允许为空。
 */
@Entity(tableName = "tricky_words")
data class TrickyWordEntity(
    @PrimaryKey val word: String,
    val gloss: String = "",
    val ipa: String = "",
    val createdAtSec: Long,
)

/**
 * 双向易混关联。只存一份有序对（[wordA] <= [wordB]），查询时两边都看，
 * 所以 affect→effect 与 effect→affect 天然是同一行，不会出现单向关系。
 */
@Entity(tableName = "tricky_links", primaryKeys = ["wordA", "wordB"], indices = [Index("wordB")])
data class TrickyLinkEntity(
    val wordA: String,
    val wordB: String,
    val createdAtSec: Long,
)

/**
 * 一个词的出题素材只留一份：英文释义、例句（带一句中文对照）和易混候选池存下来，
 * 以后考这个词都读这一行，不再发第二次请求。[distractors] 是 `[{"w":"","zh":""}]`
 * 这样的小 JSON，读它的是 `core.quiz`，这里只负责存字符串。
 *
 * 主键是小写词面而不是词库 id：素材跟着"这个词"走，不跟着"这条词库记录"走。
 * 删了再加回来的词、只在词典里还没进词库的词、素材包里灌进来的几千个考研词，都靠词面认得出。
 */
@Entity(tableName = "quiz_material")
data class QuizMaterialEntity(
    @PrimaryKey val word: String,
    val definition: String = "",
    val example: String = "",
    val exampleZh: String = "",
    val distractors: String = "[]",
    /** 搭配短语池，形状同 [distractors]：`[{"w":"take measures","zh":"采取措施"}]`。 */
    val collocations: String = "[]",
    val updatedAtSec: Long,
)

/**
 * 没做完的那一组题：题目、做到第几题、已答对几题都塞进 [payload]。
 * 主键是 `DUE` / `EARLY`，两种入口各留一份，互不覆盖；做完或换组时按入口删除。
 */
@Entity(tableName = "quiz_session")
data class QuizSessionEntity(
    @PrimaryKey val mode: String,
    val payload: String,
)

/** `sync_lineage` 只有一行，永远是这个 id。 */
const val SYNC_LINEAGE_ROW = 1

/**
 * 本机与云端的血缘。判定"把这份拉下来会不会丢掉本机刚写的记录"靠的就是这一行，
 * 所以它必须在库里而不是 SharedPreferences 里——清数据或换机时丢了它，
 * 下次下载就再也问不出"你到底有没有动过"。
 * [syncedName] 空串表示这台机器从来没和云端对齐过。
 */
@Entity(tableName = "sync_lineage")
data class SyncLineageEntity(
    @PrimaryKey val id: Int = SYNC_LINEAGE_ROW,
    val device: String = "",
    val syncedName: String = "",
    val syncedWords: Int = 0,
    val syncedChapters: Int = 0,
    val syncedReviews: Int = 0,
    val syncedLastReviewId: Long = 0,
    /** 素材 / 易错 / 故事档案 / 室友那些表的行数指纹；空串 = 升级前留下的老行，判定时当"对不上"。 */
    val syncedExtras: String = "",
    val lastUploadName: String = "",
    val lastUploadSec: Long = 0,
    val lastRestoreName: String = "",
    val lastRestoreSec: Long = 0,
)
