package com.immerlex.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.ui.ImmerlexColors

/**
 * 设置页的每一节都收成一条标题：折起来只剩"现在设成了什么"那一行，点开才是控件。
 * 一次只摊开一节——整页八个区同时展开时，找一样设置要滚两屏。
 */
@Composable
internal fun DisclosureSection(
    title: String,
    summary: String,
    opened: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!opened) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmerlexColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = ImmerlexColors.Meta,
                modifier = Modifier.rotate(if (opened) 180f else 0f),
            )
        }
        if (opened) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** 不用设、只想看一眼的那一节：没有可折叠的东西，标题下面直接给数。 */
@Composable
internal fun InfoSection(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 互斥的一排档位：选中的那颗实心，其余描边。密度与提前复习组数都用它。 */
@Composable
internal fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onPick: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(onClick = { onPick(value) }, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onPick(value) }, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
                    Text(label)
                }
            }
        }
    }
}
