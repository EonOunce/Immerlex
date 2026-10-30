package com.immerlex.feature.roommate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.FishPill
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.db.RoommateWorkEntity
import com.immerlex.core.roommate.OfferCard
import com.immerlex.core.roommate.ShopView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSheet(
    shop: ShopView?,
    writingWork: Boolean,
    onWrite: () -> Unit,
    onBuyOffer: (String) -> Unit,
    onBuyWork: (Long) -> Unit,
    onReadWork: (RoommateWorkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.room_shop),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                FishPill(shop?.balance ?: 0)
            }

            SectionTitle(stringResource(R.string.room_shop_works))
            Button(onClick = onWrite, enabled = !writingWork) {
                Text(stringResource(if (writingWork) R.string.room_writing else R.string.room_write_work))
            }
            if (shop != null && shop.works.isEmpty()) {
                Text(
                    stringResource(R.string.room_shop_no_works),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImmerlexColors.Meta,
                )
            }
            shop?.works?.forEach { work ->
                WorkRow(
                    work = work,
                    affordable = shop.balance >= work.price,
                    onBuy = onBuyWork,
                    onRead = onReadWork,
                )
            }

            shop?.near?.let { near ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(stringResource(R.string.room_near_label))
                NearCard(near = near, onBuy = onBuyOffer)
            }

            if (shop != null && shop.gifts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(stringResource(R.string.room_shop_gifts))
                shop.gifts.forEach { offer -> OfferRow(offer, onBuyOffer) }
            }

            if (shop != null && shop.stall.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(stringResource(R.string.room_shop_stall))
                shop.stall.forEach { offer -> OfferRow(offer, onBuyOffer) }
            }

            shop?.recipe?.let { recipe ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(stringResource(R.string.room_shop_recipe))
                RecipeCard(recipe = recipe, onBuy = onBuyOffer)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun WorkRow(
    work: RoommateWorkEntity,
    affordable: Boolean,
    onBuy: (Long) -> Unit,
    onRead: (RoommateWorkEntity) -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(work.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.room_shop_by_it),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
            if (work.purchased) {
                TextButton(onClick = { onRead(work) }) { Text(stringResource(R.string.room_read)) }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    FishPrice(work.price)
                    FilledTonalButton(onClick = { onBuy(work.id) }, enabled = affordable) {
                        Text(stringResource(R.string.room_shop_buy))
                    }
                }
            }
        }
    }
}

@Composable
private fun NearCard(near: OfferCard, onBuy: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(RoomGlyph.GIFT, Modifier.size(18.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(near.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (near.purchased) {
                            stringResource(R.string.room_shop_owned)
                        } else if (near.gap > 0) {
                            stringResource(R.string.room_near_gap, near.gap)
                        } else {
                            stringResource(R.string.room_near_ready)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmerlexColors.Meta,
                    )
                }
                if (!near.purchased) {
                    Column(horizontalAlignment = Alignment.End) {
                        FishPrice(near.price)
                        FilledTonalButton(onClick = { onBuy(near.offerId) }, enabled = near.affordable) {
                            Text(stringResource(R.string.room_shop_buy))
                        }
                    }
                }
            }
            if (!near.purchased) {
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
            Text(near.story, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Body)
        }
    }
}

@Composable
private fun OfferRow(offer: OfferCard, onBuy: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(offer.name, style = MaterialTheme.typography.titleSmall)
                    FishPrice(offer.price)
                }
                Text(
                    offer.story,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (offer.purchased) {
                Text(
                    stringResource(R.string.room_shop_owned),
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                )
            } else {
                FilledTonalButton(onClick = { onBuy(offer.offerId) }, enabled = offer.affordable) {
                    Text(stringResource(R.string.room_shop_buy))
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: OfferCard, onBuy: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall)
                Text(recipe.story, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Body)
            }
            Column(horizontalAlignment = Alignment.End) {
                FishPrice(recipe.price)
                FilledTonalButton(onClick = { onBuy(recipe.offerId) }, enabled = recipe.affordable) {
                    Text(stringResource(R.string.room_shop_extra))
                }
            }
        }
    }
}
