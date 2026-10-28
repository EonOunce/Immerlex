package com.immerlex.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.toUiMessage
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.ChapterGenerator
import com.immerlex.core.ai.ChapterParser
import com.immerlex.core.ai.GenerateOutcome
import com.immerlex.core.ai.Segment
import com.immerlex.core.ai.ShortTone
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.READING_BOOK_PREFIX
import com.immerlex.core.db.READING_SHORT_PREFIX
import com.immerlex.core.db.ReadingPositionDao
import com.immerlex.core.db.ReadingPositionEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.db.bookReadingKey
import com.immerlex.core.db.shortReadingKey
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.notify.GenerationNotifier
import com.immerlex.core.reader.ChapterResolver
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.GradeMode
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.schedule.recorded
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderMode { LEARN, REVIEW }

/** 书架上的一本连载书；[chapters] 为 0 表示刚新建、还没写过正文。 */
data class BookOption(val genre: String, val chapters: Int, val updatedAtSec: Long)

data class ReaderState(
    val loading: Boolean = true,
    val hasChapter: Boolean = false,
    val chapterId: Long = 0,
    val title: String = "",
    val number: Int = 0,
    val kind: String = KIND_SERIAL,
    val book: String = "",
    val books: List<BookOption> = emptyList(),
    val shorts: List<ChapterOption> = emptyList(),
    val blocks: List<List<ReaderSegment>> = emptyList(),
    val mode: ReaderMode = ReaderMode.LEARN,
    val chapters: List<ChapterOption> = emptyList(),
    val selected: ReaderWord? = null,
    val gradeMode: GradeMode = GradeMode.Review,
    val previews: List<Pair<Rating, Int>> = emptyList(),
    val bankWords: Map<String, WordEntity> = emptyMap(),
    val graded: Int = 0,
    val generating: Boolean = false,
) {
    val wordCount: Int get() = blocks.flatten().count { it is ReaderSegment.Word }
    val isShort: Boolean get() = kind == KIND_SHORT
}

class ReaderViewModel(
    private val chapterDao: ChapterDao,
    private val wordDao: WordDao,
    private val positionDao: ReadingPositionDao,
    private val schedule: ScheduleService,
    private val prefs: AppPreferences,
    private val generator: ChapterGenerator,
    private val aiConfig: () -> AiConfig,
    private val notifier: GenerationNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun reload() = viewModelScope.launch { load() }

    fun generate() = viewModelScope.launch {
        if (_state.value.generating) return@launch
        if (!aiConfig().isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@launch
        }
        _state.value = _state.value.copy(generating = true)
        try {
            val outcome = notifier.runTracked {
                generator.generate(prefs.genre, prefs.density, prefs.dailyNewLimit)
            }
            _messages.emit(outcome.toUiMessage())
            if (outcome is GenerateOutcome.Saved) load()
        } finally {
            _state.value = _state.value.copy(generating = false)
        }
    }

    /** 短篇：单篇自足，不读也不写故事档案；成功就停在写好的这一篇上。 */
    fun generateShort(tone: ShortTone) = viewModelScope.launch {
        if (_state.value.generating) return@launch
        if (!aiConfig().isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@launch
        }
        _state.value = _state.value.copy(generating = true)
        try {
            val outcome = notifier.runTracked { generator.generateShort(tone, dailyNewLimit = prefs.dailyNewLimit) }
            _messages.emit(outcome.toUiMessage())
            if (outcome is GenerateOutcome.Saved) load(outcome.chapter.id)
        } finally {
            _state.value = _state.value.copy(generating = false)
        }
    }

    /** 重写本章：换一批同词的正文，编号与词的复习状态都不动。 */
    fun rewrite() = viewModelScope.launch {
        val chapterId = _state.value.chapterId
        if (chapterId == 0L || _state.value.generating) return@launch
        if (!aiConfig().isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@launch
        }
        _state.value = _state.value.copy(generating = true)
        try {
            val outcome = notifier.runTracked { generator.rewrite(chapterId, prefs.density) }
            _messages.emit(
                if (outcome is GenerateOutcome.Saved) {
                    UiMessage(R.string.reader_rewritten, listOf(outcome.chapter.number, outcome.chapter.title))
                } else {
                    outcome.toUiMessage()
                },
            )
            if (outcome is GenerateOutcome.Saved) load(chapterId)
        } finally {
            _state.value = _state.value.copy(generating = false)
        }
    }

    /** 删掉正文不等于删掉学习进度：这里只动 chapters 与阅读位置，一个词都不碰。 */
    fun deleteChapter(id: Long) = viewModelScope.launch {
        val before = _state.value
        val target = chapterDao.byId(id)
        val number = before.chapters.find { it.id == id }?.number ?: target?.number ?: 0
        val deletingCurrentShort = before.isShort && before.chapterId == id
        chapterDao.deleteById(id)
        positionDao.clearShortByChapter(id)
        if (deletingCurrentShort) {
            load(chapterDao.shortStories().firstOrNull()?.id)
        } else {
            load(before.chapterId.takeIf { it != id })
        }
        _messages.emit(
            if (target?.kind == KIND_SHORT) {
                UiMessage(R.string.reader_short_deleted, listOf(target.title))
            } else {
                UiMessage(R.string.reader_chapter_deleted, listOf(number))
            },
        )
    }

    fun openChapter(id: Long) = viewModelScope.launch { load(id) }

    /** 换一本书：优先回到这本书上次读到的那一章，没有就读它的最后一章。 */
    fun switchBook(genre: String) = viewModelScope.launch {
        val name = genre.trim().take(MAX_BOOK_NAME)
        if (name.isEmpty()) return@launch
        prefs.genre = name
        val remembered = positionDao.byKey(bookReadingKey(name))?.chapterId ?: 0
        val chapter = remembered.takeIf { it != 0L }?.let { chapterDao.byId(it) }
            ?.takeIf { it.kind == KIND_SERIAL && it.genre == name }
            ?: chapterDao.latest(name)
        render(chapter)
    }

    fun setMode(mode: ReaderMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun select(word: ReaderWord) = viewModelScope.launch {
        val entity = word.wordId?.let { wordDao.findById(it) }
        val access = if (entity == null) GradeMode.Reveal else schedule.gradeModeFor(entity)
        // 修改评分是从"评前状态"重算的，此刻的间隔预览会误导，所以只在真要记账时显示。
        val previews = entity?.takeIf { access.recorded }
            ?.let { word -> Rating.entries.map { it to schedule.preview(word, it) } }
            .orEmpty()
        _state.value = _state.value.copy(selected = word, gradeMode = access, previews = previews)
    }

    fun dismissCard() {
        _state.value = _state.value.copy(selected = null, previews = emptyList())
    }

    fun grade(rating: Rating) {
        val selected = _state.value.selected ?: return
        viewModelScope.launch {
            val entity = selected.wordId?.let { wordDao.findById(it) }
            val result = entity?.let { schedule.grade(it, rating, SOURCE_READER) }
            _state.value = _state.value.copy(
                bankWords = result?.let { _state.value.bankWords + (it.word.word.lowercase() to it.word) }
                    ?: _state.value.bankWords,
                selected = null,
                previews = emptyList(),
                graded = _state.value.graded + if (result?.recorded == true) 1 else 0,
            )
        }
    }

    private suspend fun load(chapterId: Long? = null) {
        val chapter = if (chapterId != null) {
            chapterDao.byId(chapterId) ?: rememberedChapter()
        } else {
            rememberedChapter()
        }
        render(chapter)
    }

    /**
     * 上次关掉应用时在看的东西：阅读位置表里最新的一行。指向的章节被删了，
     * 就回这本书的最后一章；整本书都没了，就回设置里的题材。
     */
    private suspend fun rememberedChapter(): ChapterEntity? {
        val position = positionDao.latest() ?: return chapterDao.latest(prefs.genre)
        val stored = position.chapterId.takeIf { it != 0L }?.let { chapterDao.byId(it) }
        if (position.key.startsWith(READING_SHORT_PREFIX)) {
            return stored ?: chapterDao.latest(prefs.genre)
        }
        val genre = position.key.removePrefix(READING_BOOK_PREFIX)
        if (genre.isNotBlank()) prefs.genre = genre
        return stored?.takeIf { it.kind == KIND_SERIAL && it.genre == prefs.genre }
            ?: chapterDao.latest(prefs.genre)
    }

    private suspend fun render(chapter: ChapterEntity?) {
        if (chapter != null && chapter.kind == KIND_SERIAL) prefs.genre = chapter.genre
        val books = booksOf(prefs.genre)
        val shorts = chapterDao.shortStories()
        val parsed = chapter?.segmentsJson?.let { ChapterParser.parse(it) }
        if (chapter == null || parsed == null) {
            remember(null)
            _state.value = _state.value.copy(
                loading = false,
                hasChapter = false,
                chapterId = 0,
                title = "",
                number = 0,
                kind = KIND_SERIAL,
                book = prefs.genre,
                books = books,
                shorts = shorts,
                chapters = emptyList(),
                blocks = emptyList(),
                selected = null,
                previews = emptyList(),
            )
            return
        }
        remember(chapter)
        val lookups = parsed.paragraphs.flatten()
            .filterIsInstance<Segment.Embedded>()
            .map { it.word.lookup }
            .distinct()
        val bank = ChapterResolver.bankIndex(wordDao.findByWords(lookups))
        val chapters = if (chapter.kind == KIND_SERIAL) {
            chapterDao.titlesForGenre(chapter.genre)
        } else {
            emptyList()
        }
        _state.value = _state.value.copy(
            loading = false,
            hasChapter = true,
            chapterId = chapter.id,
            title = chapter.title,
            number = chapter.number,
            kind = chapter.kind,
            book = prefs.genre,
            books = books,
            shorts = shorts,
            blocks = ChapterResolver.resolve(parsed, bank),
            bankWords = bank,
            chapters = chapters,
            selected = null,
            previews = emptyList(),
        )
    }

    private suspend fun booksOf(current: String): List<BookOption> {
        val rows = chapterDao.serialBooks().map { BookOption(it.genre, it.chapters, it.updatedAtSec) }
        return if (rows.any { it.genre == current }) rows else rows + BookOption(current, 0, 0)
    }

    /** 阅读位置：连载按书记，短篇按篇记；最新写入的时间戳决定下次打开落在哪里。 */
    private suspend fun remember(chapter: ChapterEntity?) {
        val key = when {
            chapter == null -> bookReadingKey(prefs.genre)
            chapter.kind == KIND_SHORT -> shortReadingKey(chapter.id)
            else -> bookReadingKey(chapter.genre)
        }
        positionDao.upsert(ReadingPositionEntity(key, chapter?.id ?: 0, Instant.now().epochSecond))
    }

    private companion object {
        const val SOURCE_READER = "reader"
        const val MAX_BOOK_NAME = 20
    }
}
