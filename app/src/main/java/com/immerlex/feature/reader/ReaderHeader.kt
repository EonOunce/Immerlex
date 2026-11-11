package com.immerlex.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.PillChip
import com.immerlex.app.ui.PillSegmented
import com.immerlex.app.ui.RoundKey

/**
 * 阅读页顶栏：左边目录、中间标题、右边更多，下面一条进度与「只留英文」，再下面是学习/复习。
 * 正文不装卡（用户定的），所以这一条是页面上唯一有边界的东西。
 */
@Composable
internal fun ReaderHeader(
    title: String,
    book: String,
    number: Int,
    mode: ReaderMode,
    wordCount: Int,
    learned: Int,
    recalled: Int,
    loading: Boolean,
    hasChapter: Boolean,
    isShort: Boolean,
    generating: Boolean,
    englishOnly: Boolean,
    chaptersAsSheet: Boolean,
    onSelectMode: (ReaderMode) -> Unit,
    onToggleEnglish: () -> Unit,
    onGenerate: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenShelf: () -> Unit,
    onRewrite: () -> Unit,
    onDelete: () -> Unit,
) {
    val serial = hasChapter && !isShort
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 平板上目录已经成栏，这里留一块同样宽的空位，标题才不会偏到一边去。
            if (serial && chaptersAsSheet) {
                RoundKey(onClick = onOpenChapters) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.reader_chapters_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (loading) stringResource(R.string.reader_loading) else title.ifBlank { book },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!loading && title.isNotBlank() && title != book) {
                    Text(
                        book,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ChapterMenu(
                serial = serial,
                generating = generating,
                onOpenShelf = onOpenShelf,
                onGenerate = onGenerate,
                onRewrite = onRewrite,
                onDelete = onDelete,
            )
        }
        if (hasChapter) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isShort) {
                        stringResource(R.string.reader_short_progress, wordCount, learned, recalled)
                    } else {
                        stringResource(R.string.reader_progress, number, wordCount, learned, recalled)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillChip(
                    text = stringResource(
                        if (englishOnly) R.string.reader_english_only_on else R.string.reader_english_only,
                    ),
                    onClick = onToggleEnglish,
                    active = englishOnly,
                )
            }
        }
        PillSegmented(
            options = ReaderMode.entries.map { it to stringResource(modeLabel(it)) },
            selected = mode,
            onPick = onSelectMode,
            modifier = Modifier.fillMaxWidth(),
        )
        if (generating) {
            Text(
                stringResource(R.string.reader_generating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 书架与本章的三个动作都收在这颗圆键里；短篇没有章，所以只剩书架那一行。 */
@Composable
private fun ChapterMenu(
    serial: Boolean,
    generating: Boolean,
    onOpenShelf: () -> Unit,
    onGenerate: () -> Unit,
    onRewrite: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundKey(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.reader_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_shelf)) },
                onClick = { open = false; onOpenShelf() },
            )
            if (serial) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_generate)) },
                    enabled = !generating,
                    onClick = { open = false; onGenerate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_rewrite)) },
                    enabled = !generating,
                    onClick = { open = false; onRewrite() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_chapter_delete)) },
                    enabled = !generating,
                    onClick = { open = false; onDelete() },
                )
            }
        }
    }
}

internal fun modeLabel(mode: ReaderMode): Int = when (mode) {
    ReaderMode.LEARN -> R.string.reader_mode_learn
    ReaderMode.REVIEW -> R.string.reader_mode_review
}