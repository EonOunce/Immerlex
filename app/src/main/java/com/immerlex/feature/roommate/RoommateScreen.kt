package com.immerlex.feature.roommate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.db.RoommateWorkEntity
import com.immerlex.core.roommate.RoomAction
import kotlinx.coroutines.delay

private const val SHEET_NONE = 0
private const val SHEET_NOTEBOOK = 1
private const val SHEET_SHOP = 2
private const val SHEET_ASSETS = 3

private const val PAGE_ROOM = 0
private const val PAGE_CARD = 1

private const val TAB_DAYS = 0
private const val TAB_GIFTS = 1

/** 房间开着时每十几秒自己换一张脸：不点它，它也会动。 */
private const val IDLE_ROTATE_MILLIS = 16_000L

@Composable
fun RoommateScreen(vm: RoommateViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.textFor(msg)) }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    var sheet by rememberSaveable { mutableIntStateOf(SHEET_NONE) }
    var notebookTab by rememberSaveable { mutableIntStateOf(TAB_DAYS) }
    var page by rememberSaveable { mutableIntStateOf(PAGE_ROOM) }
    var reading by remember { mutableStateOf<RoommateWorkEntity?>(null) }

    fun openNotebook(tab: Int) {
        notebookTab = tab
        vm.openNotebook()
        sheet = SHEET_NOTEBOOK
    }

    fun openShop() {
        vm.openShop()
        sheet = SHEET_SHOP
    }

    val idle = !state.loading && !state.creating && state.persona != null &&
        state.assetsImported > 0 && page == PAGE_ROOM
    LaunchedEffect(idle) {
        if (idle) {
            while (true) {
                delay(IDLE_ROTATE_MILLIS)
                vm.rotateStage()
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.importAssets(uri) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = RootHandledInsets,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoomHeader(
                state = state,
                onBack = if (page == PAGE_CARD) {
                    { page = PAGE_ROOM }
                } else {
                    onBack
                },
                onOpenCard = { page = PAGE_CARD },
            )

            when {
                state.loading -> Text(
                    stringResource(R.string.room_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImmerlexColors.Meta,
                    modifier = Modifier.padding(top = 24.dp, start = 6.dp),
                )

                state.creating -> RoommateCreating(state.createStage)

                state.persona == null -> RoommateIntro(onCreate = vm::create)

                page == PAGE_CARD -> state.persona?.let { persona ->
                    RoommateCardPage(
                        draft = PersonaDraft(
                            name = persona.name,
                            nature = persona.nature,
                            pace = persona.pace,
                            face = persona.face,
                            setting = state.setting,
                        ),
                        stageFile = state.stageFile,
                        onUpdate = { name, nature, pace, face, setting ->
                            // 先回房间：等待的那几秒它已经在"收拾东西"，改完直接听见它说新的一句。
                            page = PAGE_ROOM
                            vm.updatePersona(name, nature, pace, face, setting)
                        },
                    )
                }

                else -> {
                    RoomScene(
                        file = state.stageFile,
                        bubble = state.reply ?: state.episode?.line.orEmpty(),
                        assetsImported = state.assetsImported,
                        onTap = { vm.interact(RoomAction.TOUCH) },
                    )
                    state.narration?.let { NarrationCard(it) }
                    state.episode?.let { episode ->
                        if (episode.chosen == 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChoiceChip(episode.choiceA, Modifier.weight(1f)) { vm.choose(1) }
                                ChoiceChip(episode.choiceB, Modifier.weight(1f)) { vm.choose(2) }
                            }
                        }
                    }
                    ActionRow(onAction = vm::interact)
                    state.petGiftToday?.let { gift ->
                        GiftStrip(gift = gift, onOpen = { openNotebook(TAB_GIFTS) })
                    }
                    state.near?.let { near -> NearStrip(near = near, onOpen = ::openShop) }
                    RoomDock(
                        giftCount = state.petGiftCount,
                        onNotebook = { openNotebook(TAB_DAYS) },
                        onShop = ::openShop,
                        onAssets = { sheet = SHEET_ASSETS },
                    )
                    if (state.missingAssets > 0) {
                        Text(
                            stringResource(R.string.room_missing_assets, state.missingAssets),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { sheet = SHEET_ASSETS },
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        SHEET_NOTEBOOK -> NotebookSheet(
            events = state.notebook,
            petGifts = state.petGifts,
            initialTab = notebookTab,
            onDismiss = { sheet = SHEET_NONE },
        )

        SHEET_SHOP -> ShopSheet(
            shop = state.shop,
            narration = state.shopNarration,
            writingWork = state.writingWork,
            onWrite = vm::writeWork,
            onBuyOffer = vm::buyOffer,
            onBuyWork = vm::buyWork,
            onReadWork = { reading = it },
            onDismiss = { sheet = SHEET_NONE },
        )

        SHEET_ASSETS -> AssetsSheet(
            importedCount = state.assetsImported,
            missingCount = state.missingAssets,
            importing = state.importing,
            onImport = { importPicker.launch(null) },
            onDismiss = { sheet = SHEET_NONE },
        )
    }

    reading?.let { work ->
        WorkDialog(work, onDismiss = { reading = null })
    }
}
