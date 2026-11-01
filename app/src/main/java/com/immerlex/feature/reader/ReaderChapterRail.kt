package com.immerlex.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.db.ChapterOption

/**
 * 平板上的目录栏。它只做手机上那张章节抽屉里的两件事——切章和删章，
 * 重写也放在底部；手机宽度下不出现，仍旧用标题旁的下拉。
 */
@Composable
fun ReaderChapterRail(
    chapters: List<ChapterOption>,
    currentId: Long,
    generating: Boolean,
    onPick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRewrite: () -> Unit,
) {
    Column(
        modifier = Modifier.width(READER_CHAPTER_RAIL_DP.dp).fillMaxHeight()
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Text(
            stringResource(R.string.reader_chapters_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        if (chapters.isEmpty()) {
            Text(
                stringResource(R.string.reader_chapters_empty),
                color = ImmerlexColors.Meta,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    RailRow(
                        chapter = chapter,
                        current = chapter.id == currentId,
                        onPick = { onPick(chapter.id) },
                        onDelete = { onDelete(chapter.id) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onRewrite,
            enabled = !generating && chapters.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Text(stringResource(if (generating) R.string.reader_generating else R.string.reader_rewrite))
        }
    }
}

@Composable
private fun RailRow(
    chapter: ChapterOption,
    current: Boolean,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                shape,
            )
            .clickable(onClick = onPick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(2.dp))
        Box {
            IconButton(onClick = onDelete, modifier = Modifier.height(28.dp).width(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.reader_chapter_delete),
                    tint = ImmerlexColors.Meta,
                )
            }
        }
    }
}
