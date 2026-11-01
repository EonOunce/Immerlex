package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.toUiMessage
import com.immerlex.core.material.ImportOutcome
import com.immerlex.core.material.MaterialRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MaterialUiState(
    val words: Int = 0,
    val busy: Boolean = false,
)

/** 出题素材包：导入、导出、以及本机到底有几个词带着素材。 */
class MaterialViewModel(private val material: MaterialRepository) : ViewModel() {

    private val _state = MutableStateFlow(MaterialUiState())
    val state: StateFlow<MaterialUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun importText(text: String) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val outcome = material.import(text)
            _state.value = _state.value.copy(busy = false)
            if (outcome is ImportOutcome.Imported) refresh()
            _messages.emit(outcome.toUiMessage())
        }
    }

    /** 整包文本交给调用方写盘：SAF 那一头是界面的事，core 只管这份内容。 */
    fun export(block: suspend (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val text = runCatching { material.export() }.getOrNull()
            _state.value = _state.value.copy(busy = false)
            if (text == null) {
                _messages.emit(UiMessage(R.string.material_export_failed))
                return@launch
            }
            runCatching { block(text) }.onFailure { _messages.emit(UiMessage(R.string.material_export_failed)) }
        }
    }

    private fun refresh() {
        viewModelScope.launch { _state.value = _state.value.copy(words = material.count()) }
    }
}
