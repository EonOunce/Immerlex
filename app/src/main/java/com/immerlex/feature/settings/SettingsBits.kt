package com.immerlex.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.capsule

/**
 * 设置页的每一节都是一张胶囊：折起来只剩标题和右边那半行现状，点开才是控件。
 * 一次只摊开一节——整页八个区同时展开时，找一样设置要滚两屏。
 */
@Composable
internal fun DisclosureSection(
    title: String,
    summary: String,
    opened: Boolean,
    onToggle: () -> Unit,
    alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().capsule(radius = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(
                    if (alert) LocalAccent.current else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.width(9.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            if (opened) {
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (opened) 180f else 0f),
            )
        }
        if (opened) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** 不用设、只想看一眼的那一节：没有可折叠的东西，标题下面直接给数。 */
@Composable
internal fun InfoSection(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().capsule(radius = 20.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 只通往另一页的那一节：折不起也展不开，右边一个箭头。 */
@Composable
internal fun NavSection(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().capsule(radius = 20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 接口配好之后账号密码网址都收起来，这一行是唯一回去改的入口。 */
@Composable
internal fun ConnectionLink(open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (open) R.string.settings_collapse else R.string.settings_connection),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!open) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}