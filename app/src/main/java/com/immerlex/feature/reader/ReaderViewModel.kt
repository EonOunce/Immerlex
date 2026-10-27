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
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ChapterResolver
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.ScheduleService
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
    val title: String = "",
    val number: Int = 0,
    val blocks: List<List<ReaderSegment>> = emptyList(),
    val mode: ReaderMode = ReaderMode.LEARN,
    val selected: ReaderWord? = null,
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
            val outcome = generator.generate(prefs.genre, prefs.density, prefs.dailyNewLimit)
            _messages.emit(outcome.toUiMessage())
            if (outcome is GenerateOutcome.Saved) load()
        } finally {
            _state.value = _state.value.copy(generating = false)
        }
    }

    fun setMode(mode: ReaderMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun select(word: ReaderWord) {
        val entity = word.wordId?.let { id -> _state.value.bankWords.values.find { it.id == id } }
        val previews = entity?.let {
            Rating.entries.map { rating -> rating to schedule.preview(it, rating) }
        }.orEmpty()
        _state.value = _state.value.copy(selected = word, previews = previews)
    }

    fun dismissCard() { _state.value = _state.value.copy(selected = null, previews = emptyList()) }

    fun grade(rating: Rating) {
        val selected = _state.value.selected ?: return
        viewModelScope.launch {
            val entity = selected.wordId?.let { wordDao.findById(it) }
            val updatedBank = if (entity == null) {
                _state.value.bankWords
            } else {
                val reviewed = schedule.review(entity, rating, SOURCE_READER)
                _state.value.bankWords + (reviewed.word.lowercase() to reviewed)
            }
            _state.value = _state.value.copy(
                bankWords = updatedBank,
                selected = null,
                previews = emptyList(),
                graded = _state.value.graded + if (entity == null) 0 else 1,
            )
        }
    }

    private suspend fun load() {
        val chapter = chapterDao.latest(prefs.genre)
        val parsed = chapter?.segmentsJson?.let { ChapterParser.parse(it) }
        if (chapter == null || parsed == null) {
            _state.value = _state.value.copy(loading = false, hasChapter = false, blocks = emptyList())
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
            title = chapter.title,
            number = chapter.number,
            blocks = ChapterResolver.resolve(parsed, bank),
            bankWords = bank,
        )
    }

    private companion object {
        const val SOURCE_READER = "reader"
    }
}
