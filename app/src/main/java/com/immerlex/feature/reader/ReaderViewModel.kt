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
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.notify.GenerationNotifier
import com.immerlex.core.reader.ChapterResolver
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.GradeMode
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.schedule.recorded
import com.immerlex.core.settings.AppPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderMode { LEARN, REVIEW }

data class ReaderState(
    val loading: Boolean = true,
    val hasChapter: Boolean = false,
    val chapterId: Long = 0,
    val title: String = "",
    val number: Int = 0,
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
}

class ReaderViewModel(
    private val chapterDao: ChapterDao,
    private val wordDao: WordDao,
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

    /** 删掉正文不等于删掉学习进度：这里只动 chapters，一个词都不碰。 */
    fun deleteChapter(id: Long) = viewModelScope.launch {
        val number = _state.value.chapters.find { it.id == id }?.number ?: 0
        val stayOn = _state.value.chapterId.takeIf { it != id }
        chapterDao.deleteById(id)
        load(stayOn)
        _messages.emit(UiMessage(R.string.reader_chapter_deleted, listOf(number)))
    }

    fun openChapter(id: Long) = viewModelScope.launch { load(id) }

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
        val chapter = chapterId?.let { chapterDao.byId(it) } ?: chapterDao.latest(prefs.genre)
        val chapters = chapterDao.titlesForGenre(prefs.genre)
        val parsed = chapter?.segmentsJson?.let { ChapterParser.parse(it) }
        if (chapter == null || parsed == null) {
            _state.value = _state.value.copy(
                loading = false, hasChapter = false, blocks = emptyList(), chapters = chapters,
            )
            return
        }
        val lookups = parsed.paragraphs.flatten()
            .filterIsInstance<Segment.Embedded>()
            .map { it.word.lookup }
            .distinct()
        val bank = ChapterResolver.bankIndex(wordDao.findByWords(lookups))
        _state.value = _state.value.copy(
            loading = false,
            hasChapter = true,
            chapterId = chapter.id,
            title = chapter.title,
            number = chapter.number,
            blocks = ChapterResolver.resolve(parsed, bank),
            bankWords = bank,
            chapters = chapters,
        )
    }

    private companion object {
        const val SOURCE_READER = "reader"
    }
}
