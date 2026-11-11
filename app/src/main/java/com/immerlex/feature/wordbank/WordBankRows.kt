package com.immerlex.feature.wordbank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.LocalMasteryTones
import com.immerlex.app.ui.capsule
import com.immerlex.core.db.WordEntity
import com.immerlex.core.word.Mastery
import com.immerlex.core.word.mastery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 搜索是一条白胶囊：占位字直接坐在里面，尾巴上那颗主色圆点是截图导入。 */
@Composable
internal fun SearchPill(query: String, onQuery: (String) -> Unit, onCamera: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.bank_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(LocalAccent.current.copy(alpha = 0.24f))
                .clickable(onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_camera),
                contentDescription = stringResource(R.string.bank_import_image),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 一行一个词：左边那颗点和右边那枚牌子同一个色，说的是这个词现在到了哪一步。 */
@Composable
internal fun WordRow(word: WordEntity, onClick: () -> Unit) {
    val (fill, ink) = LocalMasteryTones.current.of(word.mastery())
    Row(
        modifier = Modifier.fillMaxWidth()
            .capsule(radius = 18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(ink))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                word.word,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (word.translation.isNotEmpty()) {
                Text(
                    word.translation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(masteryLabel(word)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.clip(CircleShape).background(fill)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
            word.dueEpochSec?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    formatDue(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun masteryLabel(word: WordEntity): Int = when (word.mastery()) {
    Mastery.NEW -> R.string.mastery_new
    Mastery.LEARNING -> R.string.mastery_learning
    Mastery.MASTERED -> R.string.mastery_mastered
}

/** 行上只摆到月日；跨年了才把年份带上，不然一列全是 03-05，看不出是哪一年的三月。 */
internal fun formatDue(epochSec: Long, today: LocalDate = LocalDate.now()): String {
    val day = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate()
    return (if (day.year == today.year) DUE_THIS_YEAR else DUE_OTHER_YEAR).format(day)
}

private val DUE_THIS_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val DUE_OTHER_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")