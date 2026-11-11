package com.immerlex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.core.settings.Accent

/** 一主色一个圆点；选中的那一颗描一圈墨边、压一个勾。设置页和今日页快切共用。 */
@Composable
fun AccentSwatches(
    selected: Accent,
    onPick: (Accent) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Accent.entries.forEach { accent ->
            val isSelected = accent == selected
            val fill = accentColor(accent)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(fill)
                    .then(
                        if (isSelected) {
                            Modifier.border(width = 3.dp, color = MaterialTheme.colorScheme.onBackground, shape = CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(accentLabel(accent)),
                        tint = accentInk(accent),
                        modifier = Modifier.size(size * 0.55f),
                    )
                }
            }
        }
    }
}

/** 主色的中文档名；设置页折起来那一行也要说得出是哪一档。 */
fun accentLabel(accent: Accent): Int = when (accent) {
    Accent.WARM -> R.string.accent_warm
    Accent.CORAL -> R.string.accent_coral
}

/** 分享卡那类恒为纸色的海报，用固定墨色压主色块，不跟深色主题走。 */
val PosterInk = Color(0xFF201D1A)
