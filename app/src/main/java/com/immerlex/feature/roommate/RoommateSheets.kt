package com.immerlex.feature.roommate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.db.RM_KIND_EPISODE
import com.immerlex.core.db.RM_KIND_GIFT
import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RM_KIND_RECIPE
import com.immerlex.core.db.RM_KIND_WORK
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateWorkEntity
import com.immerlex.core.roommate.PetGiftView
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookSheet(
    events: List<RoommateEventEntity>,
    petGifts: List<PetGiftView>,
    initialTab: Int,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.room_notebook),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(stringResource(R.string.room_notebook_days)) },
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text(stringResource(R.string.room_notebook_gifts, petGifts.size)) },
                )
            }
            if (tab == 0) DayTimeline(events) else GiftList(petGifts)
        }
    }
}

@Composable
private fun DayTimeline(events: List<RoommateEventEntity>) {
    if (events.isEmpty()) {
        Text(
            stringResource(R.string.room_notebook_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Meta,
        )
        return
    }
    events.groupBy { it.dayKey }.forEach { (day, list) ->
        Text(
            dayLabel(day),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Meta,
            modifier = Modifier.padding(top = 8.dp),
        )
        list.forEach { event ->
            notebookLine(event)?.let { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor(event.kind)),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun dotColor(kind: String) = when (kind) {
    RM_KIND_EPISODE -> MaterialTheme.colorScheme.primary
    RM_KIND_GIFT, RM_KIND_RECIPE -> MaterialTheme.colorScheme.tertiary
    RM_KIND_WORK -> MaterialTheme.colorScheme.secondary
    RM_KIND_NOTE -> ImmerlexColors.Meta
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun GiftList(petGifts: List<PetGiftView>) {
    if (petGifts.isEmpty()) {
        Text(
            stringResource(R.string.room_notebook_gifts_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Meta,
        )
        return
    }
    petGifts.sortedByDescending { it.dayKey }.forEach { gift ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(RoomGlyph.GIFT, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(gift.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.room_gift_cause_day, giftCauseLabel(gift.cause), dayLabel(gift.dayKey)),
                            style = MaterialTheme.typography.labelSmall,
                            color = ImmerlexColors.Meta,
                        )
                    }
                }
                Text(gift.story, style = MaterialTheme.typography.bodyMedium)
                Text(
                    gift.line,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Meta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun notebookLine(event: RoommateEventEntity): String? {
    val body = event.body.trim()
    if (body.isEmpty()) return null
    return when (event.kind) {
        RM_KIND_WORK -> stringResource(R.string.room_notebook_work, body)
        else -> body
    }
}

@Composable
private fun dayLabel(dayKey: Long): String {
    val date = LocalDate.ofEpochDay(dayKey)
    return stringResource(R.string.room_day, date.monthValue, date.dayOfMonth)
}

@Composable
fun WorkDialog(work: RoommateWorkEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(work.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(work.body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.room_close)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsSheet(
    importedCount: Int,
    missingCount: Int,
    importing: Boolean,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.room_assets),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.room_assets_count, importedCount))
            if (missingCount > 0) {
                Text(
                    stringResource(R.string.room_assets_missing, missingCount),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.room_assets_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = ImmerlexColors.Meta,
            )
            Button(onClick = onImport, enabled = !importing) {
                Text(stringResource(if (importing) R.string.room_importing else R.string.room_import_assets))
            }
        }
    }
}
