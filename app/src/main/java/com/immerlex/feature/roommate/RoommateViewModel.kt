package com.immerlex.feature.roommate

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.roommate.Episode
import com.immerlex.core.roommate.ImportSummary
import com.immerlex.core.roommate.OfferCard
import com.immerlex.core.roommate.Persona
import com.immerlex.core.roommate.PetGiftView
import com.immerlex.core.roommate.PurchaseOutcome
import com.immerlex.core.roommate.RoomAction
import com.immerlex.core.roommate.RoomView
import com.immerlex.core.roommate.RoommateService
import com.immerlex.core.roommate.ShopView
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoommateState(
    val loading: Boolean = true,
    val persona: Persona? = null,
    val episode: Episode? = null,
    val balance: Int = 0,
    val earnedToday: Int = 0,
    val habits: List<String> = emptyList(),
    val missingAssets: Int = 0,
    val assetsImported: Int = 0,
    val petGiftCount: Int = 0,
    val petGiftToday: PetGiftView? = null,
    val near: OfferCard? = null,
    val stageFile: File? = null,
    val reply: String? = null,
    val importing: Boolean = false,
    val writingWork: Boolean = false,
    val notebook: List<RoommateEventEntity> = emptyList(),
    val petGifts: List<PetGiftView> = emptyList(),
    val shop: ShopView? = null,
)

class RoommateViewModel(private val service: RoommateService) : ViewModel() {

    private val _state = MutableStateFlow(RoommateState())
    val state: StateFlow<RoommateState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var upgradeAttempted = false

    init {
        viewModelScope.launch {
            openRoom()
            warmUp()
        }
    }

    /** 每次真正进入房间都算一次到访；它可能在这里送东西。 */
    fun refresh() = viewModelScope.launch { openRoom() }

    fun create(name: String, nature: String, pace: String, face: String) = viewModelScope.launch {
        service.createProfile(name, nature, pace, face, Instant.now())
        upgradeAttempted = false
        openRoom()
        warmUp()
    }

    fun interact(action: RoomAction) = viewModelScope.launch {
        val result = service.interact(action)
        _state.update {
            it.copy(
                stageFile = service.assetFile(result.assetId) ?: it.stageFile,
                reply = result.line,
            )
        }
    }

    fun choose(choice: Int) = viewModelScope.launch {
        val episode = _state.value.episode ?: return@launch
        if (episode.chosen != 0) return@launch
        val reply = service.choose(episode.id, choice) ?: return@launch
        openRoom()
        _state.update { it.copy(reply = reply) }
    }

    fun openNotebook() = viewModelScope.launch {
        _state.update {
            it.copy(notebook = service.notebook(), petGifts = service.petGifts())
        }
    }

    fun openShop() = viewModelScope.launch { reloadShop() }

    fun buyOffer(offerId: String) = viewModelScope.launch {
        val isRecipe = _state.value.shop?.recipe?.offerId == offerId
        when (val outcome = service.buyOffer(offerId)) {
            is PurchaseOutcome.Ok -> {
                _messages.emit(UiMessage(if (isRecipe) R.string.room_recipe_made else R.string.room_bought_item))
                outcome.unlockedRecipe?.let {
                    _messages.emit(UiMessage(R.string.room_recipe_unlocked, listOf(it)))
                }
                reloadShop()
                openRoom()
            }

            PurchaseOutcome.TooPoor -> _messages.emit(UiMessage(R.string.room_too_poor))
            PurchaseOutcome.AlreadyOwned -> _messages.emit(UiMessage(R.string.room_already_owned))
            PurchaseOutcome.Locked -> _messages.emit(UiMessage(R.string.room_offer_locked))
            PurchaseOutcome.Missing -> _messages.emit(UiMessage(R.string.room_missing_item))
        }
    }

    fun writeWork() = viewModelScope.launch {
        if (_state.value.writingWork) return@launch
        _state.update { it.copy(writingWork = true) }
        val work = try {
            service.writeWork()
        } catch (e: Exception) {
            _state.update { it.copy(writingWork = false) }
            _messages.emit(UiMessage(R.string.room_work_failed))
            return@launch
        }
        _state.update { it.copy(writingWork = false) }
        if (work == null) {
            _messages.emit(UiMessage(R.string.room_work_today_done))
        } else {
            _messages.emit(UiMessage(R.string.room_work_written, listOf(work.title)))
        }
        reloadShop()
    }

    fun buyWork(id: Long) = viewModelScope.launch {
        val work = _state.value.shop?.works?.firstOrNull { it.id == id }
        when (service.buyWork(id)) {
            is PurchaseOutcome.Ok -> {
                if (work != null) _messages.emit(UiMessage(R.string.room_bought_work, listOf(work.title)))
                reloadShop()
                openRoom()
            }

            PurchaseOutcome.TooPoor -> _messages.emit(UiMessage(R.string.room_too_poor))
            PurchaseOutcome.AlreadyOwned -> _messages.emit(UiMessage(R.string.room_already_owned))
            PurchaseOutcome.Locked -> _messages.emit(UiMessage(R.string.room_offer_locked))
            PurchaseOutcome.Missing -> _messages.emit(UiMessage(R.string.room_missing_item))
        }
    }

    fun importAssets(uri: Uri) = viewModelScope.launch {
        if (_state.value.importing) return@launch
        _state.update { it.copy(importing = true) }
        val summary = runCatching { service.importAssets(uri) }.getOrNull()
        _state.update { it.copy(importing = false) }
        if (summary == null) {
            _messages.emit(UiMessage(R.string.room_import_error))
        } else {
            emitImport(summary)
            openRoom()
        }
    }

    private suspend fun emitImport(summary: ImportSummary) {
        _messages.emit(UiMessage(R.string.room_import_done, listOf(summary.seen, summary.added)))
        if (summary.failed > 0) {
            _messages.emit(UiMessage(R.string.room_import_partial, listOf(summary.failed)))
        }
    }

    private suspend fun openRoom() {
        val view = service.enter(Instant.now())
        apply(view)
    }

    private fun apply(view: RoomView) {
        _state.update { old ->
            old.copy(
                loading = false,
                persona = view.profile,
                episode = view.episode,
                balance = view.balance,
                earnedToday = view.earnedToday,
                habits = view.habits,
                missingAssets = view.missingAssets,
                assetsImported = view.assetsImported,
                petGiftCount = view.petGiftCount,
                petGiftToday = view.petGiftToday,
                near = view.near,
                stageFile = service.assetFile(view.episode?.assetId) ?: old.stageFile,
            )
        }
    }

    /** 先补一次台词本，再把今天的内容交给模型写一遍；都只试一次。 */
    private fun warmUp() = viewModelScope.launch {
        if (upgradeAttempted) return@launch
        if (_state.value.persona == null) return@launch
        upgradeAttempted = true
        service.upgradeLinebook(Instant.now())
        val upgraded = service.upgradeDaily(Instant.now()) ?: return@launch
        apply(upgraded)
    }

    private suspend fun reloadShop() {
        val shop = service.shop()
        _state.update { it.copy(shop = shop, balance = shop.balance) }
    }
}
