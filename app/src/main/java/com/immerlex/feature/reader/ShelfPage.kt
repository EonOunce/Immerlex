package com.immerlex.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.core.ai.ShortPlan
import com.immerlex.core.ai.ShortTone
import com.immerlex.core.db.ShortRow

private val CardShape = RoundedCornerShape(20.dp)

/**
 * 题材名 → 固定色相（0–359 度，`Color.hsl` 要的就是度数）：同一本书永远是同一个颜色，
 * 扫一眼就靠颜色找到它。卡片不画成书脊或封面长条，就是一块色。
 */
internal fun shelfHue(genre: String): Float = ((genre.hashCode() % 360) + 360).toFloat() % 360f

private fun shelfPalette(seed: String, dark: Boolean): Pair<Color, Color> {
    val hue = shelfHue(seed)
    // 饱和度必须撑住：深色下压到 0.3 时，蓝和红的卡片会塌成同一种酱色，题材色就白做了。
    return Color.hsl(hue, if (dark) 0.50f else 0.62f, if (dark) 0.23f else 0.90f) to
        Color.hsl(hue, if (dark) 0.55f else 0.72f, if (dark) 0.82f else 0.26f)
}

/**
 * 书架整页。正在读的那本摊在最上面，其余连载排成色块方卡，短篇另起一区。
 * 这里只负责"换一本"和"接着读"；改正文、删章仍留在阅读页。
 */
@Composable
fun ShelfPage(
    state: ReaderState,
    onSelectBook: (String) -> Unit,
    onOpenShort: (Long) -> Unit,
    onNewBook: () -> Unit,
    onWriteShort: () -> Unit,
    onRequestDeleteShorts: (List<Long>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val dark = LocalAppearance.current.darkMode
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_shelf),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onRequestDeleteShorts(selected) }) {
                    Text(stringResource(R.string.shelf_delete_count, selected.size))
                }
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.shelf_close))
            }
        }

        if (state.hasChapter) {
            val short = state.shorts.find { it.id == state.chapterId }
            ReadingNowCard(
                title = state.title.ifBlank { state.book },
                subtitle = if (state.isShort) {
                    stringResource(R.string.shelf_short_kind)
                } else {
                    stringResource(R.string.shelf_now_reached, state.number)
                },
                seed = (if (state.isShort) state.title else state.book).ifBlank { state.title },
                tone = if (state.isShort) shortToneLabel(short) else state.book,
                dark = dark,
                onContinue = onBack,
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionHeader(
            stringResource(R.string.shelf_serial),
            stringResource(R.string.shelf_new_book),
            onNewBook,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            items(state.books, key = { "book:${it.genre}" }) { book ->
                ShelfCard(
                    label = book.genre,
                    meta = bookMeta(book),
                    seed = book.genre,
                    dark = dark,
                    current = !state.isShort && book.genre == state.book,
                    onClick = {
                        onSelectBook(book.genre)
                        onBack()
                    },
                )
            }
            if (state.shorts.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        stringResource(R.string.shelf_shorts),
                        stringResource(R.string.shelf_write_short),
                        onWriteShort,
                    )
                }
                items(state.shorts, key = { "short:${it.id}" }) { short ->
                    val checked = short.id in selected
                    ShelfCard(
                        label = short.title,
                        meta = shortToneLabel(short),
                        seed = short.title,
                        dark = dark,
                        current = state.isShort && short.id == state.chapterId,
                        checked = checked,
                        onClick = {
                            when {
                                checked -> selected = selected - short.id
                                selected.isEmpty() -> {
                                    onOpenShort(short.id)
                                    onBack()
                                }
                                else -> selected = selected + short.id
                            }
                        },
                        onLongClick = { selected = listOf(short.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = ImmerlexColors.Meta,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** 正在读的这本：卡片本身就是"回到这儿"，所以只需要一个继续读的按钮。 */
@Composable
private fun ReadingNowCard(
    title: String,
    subtitle: String,
    seed: String,
    tone: String,
    dark: Boolean,
    onContinue: () -> Unit,
) {
    val (fill, ink) = shelfPalette(seed, dark)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, CardShape)
            .padding(18.dp),
    ) {
        Text(
            tone,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            title.ifBlank { stringResource(R.string.shelf_untitled) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = ink)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onContinue, shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.shelf_continue))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
    label: String,
    meta: String,
    seed: String,
    dark: Boolean,
    current: Boolean,
    checked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val (fill, ink) = shelfPalette(seed, dark)
    val shape = if (current || checked) {
        Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CardShape)
    } else {
        Modifier
    }
    Box(
        modifier = shape
            .aspectRatio(1f)
            .background(fill, CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 2)
        }
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }
    }
}

@Composable
private fun bookMeta(book: BookOption): String = when {
    book.chapters == 0 -> stringResource(R.string.shelf_book_empty)
    book.lastReadNumber > 0 -> stringResource(
        R.string.shelf_book_read,
        book.lastReadNumber,
        DateUtils.getRelativeTimeSpanString(
            book.lastReadSec * 1000L,
            System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS,
        ),
    )

    else -> stringResource(R.string.shelf_book_meta, book.chapters)
}

@Composable
private fun shortToneLabel(short: ShortRow?): String = when {
    short == null -> ""
    short.toneText.isNotBlank() -> short.toneText
    else -> stringResource(toneLabel(ShortTone.of(short.genre)))
}

/** 写短篇：这篇想写什么（可不填）+ 腔调（三个预设，或自己说一句）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortComposer(onDismiss: () -> Unit, onWrite: (ShortPlan) -> Unit) {
    var idea by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf(ShortTone.EVERYMAN) }
    var ownTone by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shelf_short_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it.take(MAX_SHORT_IDEA) },
                    label = { Text(stringResource(R.string.short_idea_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ShortTone.entries.forEach { entry ->
                        ToneChip(stringResource(toneLabel(entry)), !custom && entry == tone) {
                            custom = false
                            tone = entry
                        }
                    }
                    ToneChip(stringResource(R.string.short_tone_custom), custom) { custom = true }
                }
                if (custom) {
                    OutlinedTextField(
                        value = ownTone,
                        onValueChange = { ownTone = it.take(MAX_SHORT_TONE) },
                        label = { Text(stringResource(R.string.short_own_tone_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onWrite(
                        ShortPlan(
                            tone = tone,
                            toneText = if (custom) ownTone.trim() else "",
                            idea = idea.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.short_write_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ToneChip(label: String, picked: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = picked,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        label = { Text(label) },
    )
}

internal fun toneLabel(tone: ShortTone): Int = when (tone) {
    ShortTone.EVERYMAN -> R.string.short_tone_everyman
    ShortTone.LUSH -> R.string.short_tone_lush
    ShortTone.COLD -> R.string.short_tone_cold
}

private const val MAX_SHORT_IDEA = 120
private const val MAX_SHORT_TONE = 80
