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
import com.immerlex.core.ai.ShortPlan
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.ReadingPositionDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.notify.GenerationNotifier
import com.immerlex.core.reader.ChapterResolver
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.schedule.WordAccess
import com.immerlex.core.settings.AppPreferences
import com.immerlex.core.tricky.TrickyCard
import com.immerlex.core.tricky.TrickyRepository
import com.immerlex.core.tricky.TrickyWord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val chapterDao: ChapterDao,
    private val wordDao: WordDao,
    private val positionDao: ReadingPositionDao,
    private val schedule: ScheduleService,
    private val trickyRepository: TrickyRepository,
    private val prefs: AppPreferences,
    private val generator: ChapterGenerator,
    private val aiConfig: () -> AiConfig,
    private val notifier: GenerationNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _undoPrompts = MutableSharedFlow<UndoPrompt>()
    val undoPrompts: SharedFlow<UndoPrompt> = _undoPrompts.asSharedFlow()

    private val bookmarks = ShelfBookmarks(chapterDao, positionDao, prefs)

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
    fun generateShort(plan: ShortPlan) = viewModelScope.launch {
        if (_state.value.generating) return@launch
        if (!aiConfig().isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@launch
        }
        _state.value = _state.value.copy(generating = true)
        try {
            val outcome = notifier.runTracked { generator.generateShort(plan, dailyNewLimit = prefs.dailyNewLimit) }
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
        val number = before.chapters.find { it.id == id }?.number ?: chapterDao.byId(id)?.number ?: 0
        chapterDao.deleteById(id)
        load(before.chapterId.takeIf { it != id })
        _messages.emit(UiMessage(R.string.reader_chapter_deleted, listOf(number)))
    }

    /** 书架上批量删短篇：只有短篇能被选中删除，连载章节要走本章目录逐章删。 */
    fun deleteShorts(ids: List<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        val current = _state.value.chapterId
        ids.forEach {
            chapterDao.deleteById(it)
            positionDao.clearShortByChapter(it)
        }
        load(if (current in ids) chapterDao.shortStories().firstOrNull()?.id else current)
        _messages.emit(UiMessage(R.string.reader_shorts_deleted, listOf(ids.size)))
    }

    fun openChapter(id: Long) = viewModelScope.launch { load(id) }

    /** 换一本书：优先回到这本书上次读到的那一章，没有就读它的最后一章。 */
    fun switchBook(genre: String) = viewModelScope.launch {
        val name = genre.trim().take(MAX_BOOK_NAME)
        if (name.isEmpty()) return@launch
        prefs.genre = name
        render(bookmarks.lastChapterOf(name))
    }

    fun setMode(mode: ReaderMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun select(word: ReaderWord) = viewModelScope.launch { selectNow(word) }

    private suspend fun selectNow(word: ReaderWord) {
        val entity = word.wordId?.let { wordDao.findById(it) }
        val access = entity?.let {
            schedule.accessFor(it, answersHidden = _state.value.mode == ReaderMode.REVIEW)
        } ?: WordAccess.ViewOnly
        val previews = entity?.takeIf { access != WordAccess.ViewOnly }
            ?.let { word -> Rating.entries.map { it to schedule.preview(word, it) } }
            .orEmpty()
        _state.value = _state.value.copy(
            selected = word,
            access = access,
            previews = previews,
            tricky = null,
        )
        val key = word.lookup.trim().lowercase()
        val card = trickyRepository.card(key)
        if (_state.value.selected?.lookup?.trim()?.lowercase() == key) {
            _state.value = _state.value.copy(tricky = card)
        }
    }

    fun dismissCard() {
        _state.value = _state.value.copy(selected = null, previews = emptyList(), tricky = null)
    }

    fun toggleTricky() {
        val word = _state.value.selected ?: return
        viewModelScope.launch {
            applyTricky(word.lookup, trickyRepository.toggle(word.lookup, word.zh, word.ipa))
        }
    }

    fun linkTricky(other: TrickyWord) {
        val word = _state.value.selected ?: return
        viewModelScope.launch {
            applyTricky(word.lookup, trickyRepository.link(baseOf(word), other))
        }
    }

    fun unlinkTricky(other: TrickyWord) {
        val word = _state.value.selected ?: return
        viewModelScope.launch {
            applyTricky(word.lookup, trickyRepository.unlink(word.lookup, other.word))
        }
    }

    fun addTrickyManual(typed: String) {
        val word = _state.value.selected ?: return
        viewModelScope.launch {
            val result = trickyRepository.addManual(baseOf(word), typed)
            applyTricky(word.lookup, result.card)
            if (!result.added) _messages.emit(UiMessage(R.string.tricky_duplicate))
        }
    }

    /** 点开一个易混词：它可能不在本章正文里，但词卡照样能开。 */
    fun openTricky(other: TrickyWord) = viewModelScope.launch {
        val detail = trickyRepository.detail(other.word)
        selectNow(
            ReaderWord(
                lookup = detail.word,
                zh = detail.gloss,
                ipa = detail.ipa,
                def = detail.definition,
                example = "",
                wordId = detail.wordId,
            ),
        )
    }

    private fun applyTricky(raw: String, card: TrickyCard) {
        val selected = _state.value.selected ?: return
        if (selected.lookup.trim().lowercase() != raw.trim().lowercase()) return
        _state.value = _state.value.copy(tricky = card)
    }

    private fun baseOf(word: ReaderWord) =
        TrickyWord(word.lookup.trim().lowercase(), word.zh, word.ipa)

    fun grade(rating: Rating) {
        val selected = _state.value.selected ?: return
        val access = _state.value.access
        viewModelScope.launch {
            val entity = selected.wordId?.let { wordDao.findById(it) }
            val result = entity?.let {
                when (access) {
                    WordAccess.Learn -> schedule.learn(it, rating, SOURCE_READER)
                    WordAccess.Recall -> schedule.recall(it, rating, SOURCE_READER)
                    WordAccess.Early -> schedule.recall(
                        it,
                        rating,
                        SOURCE_READER,
                        allowEarly = true,
                    )
                    WordAccess.ViewOnly -> null
                }
            }
            val learnedNow = result?.kind == LOG_KIND_LEARN
            _state.value = _state.value.copy(
                bankWords = result?.let { _state.value.bankWords + (it.word.word.lowercase() to it.word) }
                    ?: _state.value.bankWords,
                selected = null,
                previews = emptyList(),
                tricky = null,
                learned = _state.value.learned + if (result != null && learnedNow) 1 else 0,
                recalled = _state.value.recalled + if (result != null && !learnedNow) 1 else 0,
            )
            if (result != null) {
                _undoPrompts.emit(UndoPrompt(result.logId, result.kind))
            }
        }
    }

    fun undo(prompt: UndoPrompt) = viewModelScope.launch {
        val result = schedule.undo(prompt.logId)
        if (result == null) {
            _messages.emit(UiMessage(R.string.reader_undo_expired))
            return@launch
        }
        val learnedNow = result.kind == LOG_KIND_LEARN
        _state.value = _state.value.copy(
            bankWords = _state.value.bankWords + (result.word.word.lowercase() to result.word),
            learned = (_state.value.learned - if (learnedNow) 1 else 0).coerceAtLeast(0),
            recalled = (_state.value.recalled - if (learnedNow) 0 else 1).coerceAtLeast(0),
        )
        _messages.emit(UiMessage(R.string.reader_undo_done))
    }

    private suspend fun load(chapterId: Long? = null) {
        val chapter = if (chapterId != null) {
            chapterDao.byId(chapterId) ?: bookmarks.remembered()
        } else {
            bookmarks.remembered()
        }
        render(chapter)
    }

    private suspend fun render(chapter: ChapterEntity?) {
        if (chapter != null && chapter.kind == KIND_SERIAL) prefs.genre = chapter.genre
        val books = bookmarks.books(prefs.genre)
        val shorts = chapterDao.shortStories()
        val parsed = chapter?.segmentsJson?.let { ChapterParser.parse(it) }
        if (chapter == null || parsed == null) {
            bookmarks.remember(null)
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
                tricky = null,
                learned = 0,
                recalled = 0,
            )
            return
        }
        bookmarks.remember(chapter)
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
            tricky = null,
            learned = 0,
            recalled = 0,
        )
    }

    private companion object {
        const val SOURCE_READER = "reader"
        const val MAX_BOOK_NAME = 20
    }
}
