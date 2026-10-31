package com.immerlex.feature.tricky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.tricky.TrickyGroup
import com.immerlex.core.tricky.TrickyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrickyState(
    val groups: List<TrickyGroup> = emptyList(),
)

/**
 * 易混词管理页。组是关系算出来的，所以每次增删都整份重读，不在界面上自己拆列表。
 */
class TrickyViewModel(private val repo: TrickyRepository) : ViewModel() {

    private val _state = MutableStateFlow(TrickyState())
    val state: StateFlow<TrickyState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun addGroups(text: String) = mutate {
        val created = repo.addGroups(text)
        if (created == 0) {
            UiMessage(R.string.tricky_batch_empty)
        } else {
            UiMessage(R.string.tricky_groups_added, listOf(created))
        }
    }

    fun addToGroup(group: TrickyGroup, typed: String) = mutate {
        if (repo.addToGroup(group, typed)) null else UiMessage(R.string.tricky_duplicate)
    }

    fun removeFromGroup(group: TrickyGroup, word: String) = mutate {
        repo.removeFromGroup(group, word)
        null
    }

    fun deleteWord(word: String) = mutate {
        repo.deleteWord(word)
        null
    }

    /** 返回 null 表示这次改动不用说话：界面已经跟着分组变化了。 */
    private fun mutate(block: suspend () -> UiMessage?) {
        viewModelScope.launch {
            val message = block()
            _state.value = _state.value.copy(groups = repo.groups())
            message?.let { _messages.emit(it) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(groups = repo.groups())
        }
    }
}
