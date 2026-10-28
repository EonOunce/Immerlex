package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.probeDetailOf
import com.immerlex.app.ui.probeFailureLabel
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.BibleExpander
import com.immerlex.core.ai.BibleOutcome
import com.immerlex.core.bible.BibleSection
import com.immerlex.core.bible.StoryBibleRepository
import com.immerlex.core.db.BibleEntryEntity
import com.immerlex.core.db.CharacterStateEntity
import com.immerlex.core.settings.AppPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BibleUiState(
    val genre: String = "",
    val seed: String = "",
    val entries: List<BibleEntryEntity> = emptyList(),
    val cast: List<CharacterStateEntity> = emptyList(),
    val busy: Boolean = false,
)

/** 故事档案面板：一段想法 → 一份档案 → 逐条改 → 每章变化自动累积成人物状态表。 */
class BibleViewModel(
    private val bible: StoryBibleRepository,
    private val expander: BibleExpander,
    private val prefs: AppPreferences,
    private val aiConfig: () -> AiConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(BibleUiState(genre = prefs.genre))
    val state: StateFlow<BibleUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            // 想法只在进入面板时读一次：正在打字时不能被库里的旧值盖掉。
            _state.value = _state.value.copy(seed = bible.seedOf(prefs.genre))
            refresh()
        }
    }

    fun onSeed(value: String) {
        _state.value = _state.value.copy(seed = value)
    }

    /** 展开与重掷是同一件事：先记住想法，再让模型换掉它生成的条目（用户手写的不动）。 */
    fun expand() = mutate {
        val seed = _state.value.seed
        if (seed.isBlank()) {
            _messages.emit(UiMessage(R.string.bible_needs_seed))
            return@mutate
        }
        if (!aiConfig().isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@mutate
        }
        bible.saveSeed(prefs.genre, seed)
        when (val outcome = expander.expand(seed)) {
            is BibleOutcome.Drafted -> {
                bible.applyDraft(prefs.genre, outcome.draft)
                _messages.emit(UiMessage(R.string.bible_expanded))
            }

            is BibleOutcome.Rejected ->
                _messages.emit(UiMessage(R.string.bible_rejected, listOf(outcome.attempts)))

            is BibleOutcome.Failed -> _messages.emit(
                outcome.failure?.let {
                    UiMessage(probeFailureLabel(it), listOf(probeDetailOf(outcome.detail)))
                } ?: UiMessage(R.string.bible_failed, listOf(outcome.detail)),
            )
        }
        refresh()
    }

    fun saveEntry(id: Long, text: String) = mutate {
        bible.saveEntry(id, text)
        saved()
    }

    fun addEntry(section: BibleSection, text: String) = mutate {
        if (text.isBlank()) {
            _messages.emit(UiMessage(R.string.bible_needs_text))
        } else {
            bible.addEntry(prefs.genre, section, text)
            saved()
        }
    }

    fun deleteEntry(id: Long) = mutate {
        bible.deleteEntry(id)
        _messages.emit(UiMessage(R.string.bible_entry_deleted))
        refresh()
    }

    /** [id] 为 0 表示新增人物。 */
    fun saveCast(id: Long, name: String, role: String, status: String) = mutate {
        when {
            name.isBlank() -> _messages.emit(UiMessage(R.string.bible_needs_name))
            id == 0L && !bible.addCast(prefs.genre, name, role, status) ->
                _messages.emit(UiMessage(R.string.bible_person_exists))

            id == 0L -> saved()
            else -> {
                bible.saveCast(id, name, role, status)
                saved()
            }
        }
    }

    fun deleteCast(id: Long) = mutate {
        _messages.emit(UiMessage(R.string.bible_person_deleted, listOf(bible.deleteCast(id))))
        refresh()
    }

    private suspend fun saved() {
        _messages.emit(UiMessage(R.string.bible_saved))
        refresh()
    }

    private suspend fun refresh() {
        val genre = prefs.genre
        _state.value = _state.value.copy(
            genre = genre,
            entries = bible.entriesOf(genre),
            cast = bible.castOf(genre),
        )
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
