package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.toUiMessage
import com.immerlex.core.material.ImportOutcome
import com.immerlex.core.material.MaterialRepository
import com.immerlex.core.material.MaterialSync
import com.immerlex.core.material.MaterialSyncStep
import com.immerlex.core.settings.AppPreferences
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
    val fetching: Boolean = false,
    val step: MaterialSyncStep? = null,
    val manifestUrl: String = "",
)

/** 出题素材包：本机导入、导出一份、按清单从网上取回，以及本机到底有几个词带着素材。 */
class MaterialViewModel(
    private val material: MaterialRepository,
    private val sync: MaterialSync,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(MaterialUiState(manifestUrl = prefs.materialManifestUrl))
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

    /**
     * 按地址那一份清单，一片一片取回来、验过校验值才进库。
     * 失败也照样刷新计数：半途停下来的时候，前几片是真的已经进来了。
     */
    fun fetchFromGitHub() {
        if (_state.value.busy || _state.value.fetching) return
        prefs.materialManifestUrl = _state.value.manifestUrl
        _state.value = _state.value.copy(fetching = true, step = null)
        viewModelScope.launch {
            val outcome = sync.fetchAll { step -> _state.value = _state.value.copy(step = step) }
            _state.value = _state.value.copy(fetching = false, step = null)
            refresh()
            _messages.emit(outcome.toUiMessage())
        }
    }

    /** 填的时候不落盘：半途改的字不该把默认地址顶掉，点「取」才算数。 */
    fun onManifestUrlChange(value: String) {
        _state.value = _state.value.copy(manifestUrl = value)
    }

    /** 整包文本交给调用方写盘：SAF 那一头是界面的事，core 只管这份内容。 */
    fun export(block: suspend (String) -> Unit) = exportText({ material.export() }, block)

    /** 词面清单同一扇门：只有词、一行一个，拿去当排除表。 */
    fun exportWords(block: suspend (String) -> Unit) = exportText({ material.exportWords() }, block)

    private fun exportText(read: suspend () -> String, block: suspend (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val text = runCatching { read() }.getOrNull()
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
