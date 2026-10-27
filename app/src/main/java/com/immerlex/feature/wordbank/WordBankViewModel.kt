package com.immerlex.feature.wordbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.db.WordEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun onQueryChange(value: String) { _query.value = value }

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
}
