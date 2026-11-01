package com.immerlex.feature.wordbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.dict.DictionarySearch
import com.immerlex.core.material.MaterialEntry
import com.immerlex.core.word.wordSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class WordBankViewModel(private val repo: WordBankRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val words: StateFlow<List<WordEntity>> =
        combine(repo.observeWords(), _query) { list, q ->
            val needle = q.trim()
            if (needle.isEmpty()) {
                list
            } else {
                list.filter {
                    it.word.contains(needle, ignoreCase = true) ||
                        it.translation.contains(needle, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 词典组：词库里搜不到时，再问一次词典。等手停下来才查（每敲一个字都查一遍
     * 是拿 SQLite 当输入法），并且跟着词库走——刚"加入词库"的词立刻从这一组消失。
     */
    val dictionaryHits: StateFlow<List<DictEntry>> =
        combine(
            _query.map { it.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS),
            repo.observeWords(),
        ) { needle, bank ->
            if (needle.length < DictionarySearch.MIN_LENGTH) {
                emptyList()
            } else {
                repo.dictionarySearch(needle, bank.map { wordSurface(it.word) }.toSet())
            }
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun onQueryChange(value: String) { _query.value = value }

    suspend fun materialFor(word: String): MaterialEntry? = repo.materialOf(word)

    suspend fun allWords(): List<WordEntity> = repo.allWords()

    fun addWord(word: String, gloss: String) = launchBusy {
        val message = if (repo.addManual(word, gloss)) {
            UiMessage(R.string.bank_added)
        } else {
            UiMessage(R.string.bank_duplicate)
        }
        _messages.emit(message)
    }

    fun importText(text: String, source: String = "import") = launchBusy {
        val summary = repo.importText(text, source)
        _messages.emit(
            UiMessage(
                R.string.bank_import_result,
                listOf(summary.parsed, summary.added, summary.duplicates, summary.missingGloss),
            ),
        )
    }

    fun delete(word: WordEntity) = launchBusy {
        repo.delete(word.id)
        _messages.emit(UiMessage(R.string.bank_deleted, listOf(word.word)))
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 220L
    }
}
