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
import com.immerlex.core.roommate.RoomMoment
import com.immerlex.core.roommate.RoomView
import com.immerlex.core.roommate.RoommateService
import com.immerlex.core.roommate.RoommateSetupStage
import com.immerlex.core.roommate.ShopView
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
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
    val setting: String = "",
    val creating: Boolean = false,
    val createStage: RoommateSetupStage? = null,
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
    val narration: String? = null,
    val shopNarration: String? = null,
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
    private var surfacedGiftId: Long? = null

    init {
        viewModelScope.launch {
            openRoom()
            warmUp()
        }
    }

    /** 每次真正进入房间都算一次到访；它可能在这里送东西。 */
    fun refresh() = viewModelScope.launch { openRoom() }

    /** 建档要等模型：第一句话和整本台词都在这一步生成，之后不再后台补。 */
    fun create(name: String, nature: String, pace: String, face: String, setting: String) =
        viewModelScope.launch {
            if (_state.value.creating) return@launch
            _state.update { it.copy(creating = true, createStage = RoommateSetupStage.FIRST_LINE) }
            val created = try {
                service.createProfile(name, nature, pace, face, setting, Instant.now()) { stage ->
                    _state.update { it.copy(createStage = stage) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(creating = false, createStage = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, createStage = null) }
                _messages.emit(UiMessage(R.string.room_setup_failed))
                return@launch
            }
            upgradeAttempted = false
            openRoom()
            _state.update { it.copy(creating = false, createStage = null, reply = created.introLine) }
            warmUp()
        }

    /**
     * 改名片：名字、三样底色、设定变了，只有第一句话和台词本跟着重发。
     * 余额、小本子、它送的东西、已经买下的都留在原处，所以这里一次都不碰它们。
     */
    fun updatePersona(name: String, nature: String, pace: String, face: String, setting: String) =
        viewModelScope.launch {
            if (_state.value.creating) return@launch
            _state.update { it.copy(creating = true, createStage = RoommateSetupStage.FIRST_LINE) }
            val updated = try {
                service.updatePersona(name, nature, pace, face, setting, Instant.now()) { stage ->
                    _state.update { it.copy(createStage = stage) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(creating = false, createStage = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, createStage = null) }
                _messages.emit(UiMessage(R.string.room_setup_failed))
                return@launch
            }
            if (updated == null) {
                _state.update { it.copy(creating = false, createStage = null) }
                return@launch
            }
            openRoom()
            _state.update {
                it.copy(
                    creating = false,
                    createStage = null,
                    persona = updated.persona,
                    reply = updated.introLine,
                    narration = null,
                )
            }
        }

    fun interact(action: RoomAction) = viewModelScope.launch {
        val result = service.interact(action)
        _state.update {
            it.copy(
                stageFile = service.assetFile(result.assetId) ?: it.stageFile,
                reply = result.line,
                narration = null,
            )
        }
    }

    fun rotateStage() = viewModelScope.launch {
        val assetId = service.idleAsset() ?: return@launch
        val file = service.assetFile(assetId) ?: return@launch
        _state.update { it.copy(stageFile = file) }
    }

    fun choose(choice: Int) = viewModelScope.launch {
        val episode = _state.value.episode ?: return@launch
        if (episode.chosen != 0) return@launch
        val reply = service.choose(episode.id, choice) ?: return@launch
        openRoom()
        _state.update { it.copy(reply = reply, narration = null) }
    }

    fun openNotebook() = viewModelScope.launch {
        _state.update {
            it.copy(notebook = service.notebook(), petGifts = service.petGifts())
        }
    }

    /** 开小铺时清掉上一次买下去的旁白：小铺里只显示刚才那一下，房间里的原话不动。 */
    fun openShop() = viewModelScope.launch {
        _state.update { it.copy(shopNarration = null) }
        reloadShop()
    }

    fun buyOffer(offerId: String) = viewModelScope.launch {
        val isRecipe = _state.value.shop?.recipe?.offerId == offerId
        val purchase = service.buy(offerId)
        when (val outcome = purchase.outcome) {
            is PurchaseOutcome.Ok -> {
                _messages.emit(UiMessage(if (isRecipe) R.string.room_recipe_made else R.string.room_bought_item))
                outcome.unlockedRecipe?.let {
                    _messages.emit(UiMessage(R.string.room_recipe_unlocked, listOf(it)))
                }
                reloadShop()
                openRoom()
                // 房间刷新时可能把「它送的东西」顶上来，但刚买下这件东西的话更新，让它说这句。
                purchase.moment?.let(::surface)
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
        val gift = view.petGiftToday?.takeIf { it.id != surfacedGiftId }
        gift?.let { surfacedGiftId = it.id }
        _state.update { old ->
            old.copy(
                loading = false,
                persona = view.profile,
                setting = view.setting,
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
                reply = gift?.line?.ifBlank { null } ?: old.reply,
                narration = gift?.story?.ifBlank { null } ?: old.narration,
            )
        }
    }

    /** 刚发生的一件事：它说的话进对话框，旁白落在房间下面；小铺开着时同一句旁白也顶到标题下面。 */
    private fun surface(moment: RoomMoment) {
        _state.update {
            it.copy(
                reply = moment.line.ifBlank { it.reply },
                narration = moment.narration.ifBlank { null },
                shopNarration = moment.narration.ifBlank { null },
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
