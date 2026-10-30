package com.immerlex.feature.roommate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.FishPill
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.db.RoommateWorkEntity
import com.immerlex.core.roommate.OfferCard
import com.immerlex.core.roommate.PetGiftView
import com.immerlex.core.roommate.RoomAction

private const val SHEET_NONE = 0
private const val SHEET_NOTEBOOK = 1
private const val SHEET_SHOP = 2
private const val SHEET_ASSETS = 3

private const val TAB_DAYS = 0
private const val TAB_GIFTS = 1

@Composable
fun RoommateScreen(vm: RoommateViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.textFor(msg)) }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.importAssets(uri) }

    var sheet by rememberSaveable { mutableIntStateOf(SHEET_NONE) }
    var notebookTab by rememberSaveable { mutableIntStateOf(TAB_DAYS) }
    var reading by remember { mutableStateOf<RoommateWorkEntity?>(null) }

    fun openNotebook(tab: Int) {
        notebookTab = tab
        vm.openNotebook()
        sheet = SHEET_NOTEBOOK
    }

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
            Header(state, onBack)

            when {
                state.loading -> Text(
                    stringResource(R.string.room_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImmerlexColors.Meta,
                    modifier = Modifier.padding(top = 24.dp, start = 6.dp),
                )

                state.persona == null -> RoommateIntro(onCreate = vm::create)

                else -> {
                    RoomScene(
                        file = state.stageFile,
                        bubble = state.reply ?: state.episode?.line.orEmpty(),
                        assetsImported = state.assetsImported,
                        onTap = { vm.interact(RoomAction.TOUCH) },
                    )
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
                    state.near?.let { near ->
                        NearStrip(near = near, onOpen = {
                            vm.openShop()
                            sheet = SHEET_SHOP
                        })
                    }
                    Dock(
                        giftCount = state.petGiftCount,
                        onNotebook = { openNotebook(TAB_DAYS) },
                        onShop = {
                            vm.openShop()
                            sheet = SHEET_SHOP
                        },
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

@Composable
private fun Header(state: RoommateState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.persona?.name ?: stringResource(R.string.room_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (state.habits.isNotEmpty()) {
                Text(
                    state.habits.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.persona != null) {
            FishPill(state.balance)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ActionRow(onAction: (RoomAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        ActionButton(
            glyph = RoomGlyph.HEART,
            label = stringResource(R.string.room_touch),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { onAction(RoomAction.TOUCH) }
        ActionButton(
            glyph = RoomGlyph.BUBBLE,
            label = stringResource(R.string.room_talk),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        ) { onAction(RoomAction.TALK) }
        ActionButton(
            glyph = RoomGlyph.DOOR,
            label = stringResource(R.string.room_outing),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        ) { onAction(RoomAction.OUTING) }
    }
}

@Composable
private fun ActionButton(
    glyph: RoomGlyph,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(glyph, Modifier.size(26.dp), tint = content)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ImmerlexColors.Body)
    }
}

@Composable
private fun GiftStrip(gift: PetGiftView, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(RoomGlyph.GIFT, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.room_gift_today, gift.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    gift.line,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.room_gift_peek),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NearStrip(near: OfferCard, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph(RoomGlyph.GIFT, Modifier.size(18.dp), MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(near.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (near.price <= 0) 1f else (near.price - near.gap).toFloat() / near.price
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                if (near.gap > 0) stringResource(R.string.room_near_gap, near.gap) else stringResource(R.string.room_near_ready),
                style = MaterialTheme.typography.labelMedium,
                color = ImmerlexColors.Meta,
            )
        }
    }
}

@Composable
private fun Dock(
    giftCount: Int,
    onNotebook: () -> Unit,
    onShop: () -> Unit,
    onAssets: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DockTile(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = stringResource(R.string.room_notebook),
            badge = giftCount,
            modifier = Modifier.weight(1f),
            onClick = onNotebook,
        )
        DockTile(
            icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = stringResource(R.string.room_shop),
            modifier = Modifier.weight(1f),
            onClick = onShop,
        )
        DockTile(
            icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = stringResource(R.string.room_assets),
            modifier = Modifier.weight(1f),
            onClick = onAssets,
        )
    }
}

@Composable
private fun DockTile(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                icon()
                if (badge > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 14.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
