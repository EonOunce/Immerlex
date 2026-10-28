package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.probeDetailOf
import com.immerlex.app.ui.probeFailureLabel
import com.immerlex.app.ui.toUiMessage
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.ApiKeyStore
import com.immerlex.core.ai.ChapterGenerator
import com.immerlex.core.ai.Density
import com.immerlex.core.ai.OpenAiCompatClient
import com.immerlex.core.ai.ProviderPreset
import com.immerlex.core.db.WordDao
import com.immerlex.core.settings.AppPreferences
import com.immerlex.core.settings.DEFAULT_FONT_SCALE
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val keyPersistent: Boolean = true,
    val genre: String = "",
    val density: Density = Density.MEDIUM,
    val dailyNewLimit: Int = AppPreferences.DEFAULT_NEW_LIMIT,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: Boolean = false,
    /** Model IDs reported by the gateway, or the preset's guesses before a probe. */
    val models: List<String> = emptyList(),
    val bankTotal: Int = 0,
    val bankNew: Int = 0,
    val bankDue: Int = 0,
    val busy: Boolean = false,
)

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val keyStore: ApiKeyStore,
    private val generator: ChapterGenerator,
    private val wordDao: WordDao,
) : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun onBaseUrl(value: String) { _state.value = _state.value.copy(baseUrl = value) }
    fun onModel(value: String) { _state.value = _state.value.copy(model = value) }
    fun onApiKey(value: String) { _state.value = _state.value.copy(apiKey = value) }
    fun onGenre(value: String) { _state.value = _state.value.copy(genre = value) }
    fun onDensity(value: Density) { _state.value = _state.value.copy(density = value) }
    fun onNewLimit(value: Int) { _state.value = _state.value.copy(dailyNewLimit = value.coerceIn(1, 200)) }

    /** Appearance writes through immediately: the theme and reader observe AppPreferences.appearance. */
    fun onFontScale(delta: Float) = applyAppearance(_state.value.fontScale + delta, _state.value.darkMode)

    fun onDarkMode(value: Boolean) = applyAppearance(_state.value.fontScale, value)

    private fun applyAppearance(fontScale: Float, darkMode: Boolean) {
        prefs.fontScale = fontScale
        prefs.darkMode = darkMode
        _state.value = _state.value.copy(fontScale = prefs.fontScale, darkMode = prefs.darkMode)
    }

    fun save() = mutate {
        prefs.baseUrl = _state.value.baseUrl
        prefs.model = _state.value.model
        prefs.genre = _state.value.genre.ifBlank { "宫斗" }
        prefs.density = _state.value.density
        prefs.dailyNewLimit = _state.value.dailyNewLimit
        keyStore.save(_state.value.apiKey)
        _state.value = load().copy(models = _state.value.models)
        _messages.emit(UiMessage(R.string.settings_saved))
    }

    fun testConnection() = mutate {
        val config = draftConfig()
        if (!config.isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@mutate
        }
        if (config.apiKey.isBlank()) {
            _messages.emit(UiMessage(R.string.settings_probe_missing_key))
            return@mutate
        }
        val result = OpenAiCompatClient(config).probe()
        if (result.ok) {
            // The gateway's own list is the only trustworthy source of model IDs: product
            // names and API names differ, and the user cannot tell them apart by eye.
            _state.value = _state.value.copy(models = result.models)
            _messages.emit(
                UiMessage(
                    if (result.models.isEmpty()) R.string.settings_probe_ok
                    else R.string.settings_probe_ok_models,
                    listOf(result.models.size),
                ),
            )
        } else {
            _messages.emit(
                UiMessage(
                    probeFailureLabel(result.failure),
                    listOf(probeDetailOf(result.detail)),
                ),
            )
        }
    }

    /** Fills the address the user keeps mistyping and offers its known model IDs. */
    fun applyPreset(preset: ProviderPreset) {
        _state.value = _state.value.copy(
            baseUrl = preset.baseUrl,
            models = preset.knownModels,
            model = _state.value.model.ifBlank { preset.knownModels.firstOrNull().orEmpty() },
        )
    }

    fun onPickModel(value: String) {
        _state.value = _state.value.copy(model = value)
    }

    fun generateChapter() = mutate {
        val config = draftConfig()
        if (!config.isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@mutate
        }
        val outcome = generator.generate(prefs.genre, prefs.density, prefs.dailyNewLimit)
        _messages.emit(outcome.toUiMessage())
    }

    /** Runs an action with the busy flag set, then refreshes the bank counters. */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(busy = false)
                refreshCounts()
            }
        }
    }

    private fun load(): SettingsState = SettingsState(
        baseUrl = prefs.baseUrl,
        model = prefs.model,
        apiKey = keyStore.key(),
        keyPersistent = keyStore.isPersistent,
        genre = prefs.genre,
        density = prefs.density,
        dailyNewLimit = prefs.dailyNewLimit,
        fontScale = prefs.fontScale,
        darkMode = prefs.darkMode,
    )

    private suspend fun refreshCounts() {
        _state.value = _state.value.copy(
            bankTotal = wordDao.totalCount(),
            bankNew = wordDao.newCount(),
            bankDue = wordDao.dueCount(Instant.now().epochSecond),
        )
    }

    private fun draftConfig() = AiConfig(
        baseUrl = _state.value.baseUrl,
        apiKey = _state.value.apiKey,
        model = _state.value.model,
    )

    init {
        viewModelScope.launch { refreshCounts() }
    }
}
