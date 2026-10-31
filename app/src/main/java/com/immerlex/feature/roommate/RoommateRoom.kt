package com.immerlex.feature.roommate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.FishPill
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.roommate.OfferCard
import com.immerlex.core.roommate.PetGiftView
import com.immerlex.core.roommate.RoomAction

@Composable
fun RoomHeader(
    state: RoommateState,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
) {
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
            NamePill(state.persona?.name ?: stringResource(R.string.room_title), onOpenCard)
            if (state.habits.isNotEmpty()) {
                Text(
                    state.habits.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, start = 10.dp),
                )
            }
        }
        if (state.persona != null) {
            FishPill(state.balance)
        }
    }
}

/** 名字本身就是一张名片：点进去能改它怎么说话。 */
@Composable
private fun NamePill(name: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 刚发生的事落在房间下面：气泡里是它说的话，这一行是旁白。 */
@Composable
fun NarrationCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ChoiceChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
fun ActionRow(onAction: (RoomAction) -> Unit) {
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
fun GiftStrip(gift: PetGiftView, onOpen: () -> Unit) {
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
fun NearStrip(near: OfferCard, onOpen: () -> Unit) {
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
fun RoomDock(
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
