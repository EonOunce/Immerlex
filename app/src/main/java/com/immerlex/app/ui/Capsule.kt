package com.immerlex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 app 那张胶囊的皮：圆角 + 淡投影 + 比页面底色亮一档的面。今日页、战绩页、词库行、
 * 设置分区共用它，省得每页各调一遍圆角与投影。要点击就接在它后面，涟漪才会被裁进圆角。
 */
@Composable
fun Modifier.capsule(
    radius: Dp = 22.dp,
    elevation: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(elevation, shape).clip(shape).background(color)
}

/** 顶栏上那颗圆键（阅读页的目录与更多）。图标由调用方给，这里只管那一圈白底。 */
@Composable
fun RoundKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 互斥的一排档位，选中那颗压深底。深浅两套都靠 inverseSurface 翻面，
 * 所以深色下选中项是亮的、其余是暗的，不会两颗都糊在一起。
 */
@Composable
fun <T> PillSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.shadow(3.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.inverseSurface else Color.Transparent)
                    .clickable(enabled = enabled) { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一颗可点的白胶囊；[active] 为真时压深底，用来表示"这个开关现在开着"。 */
@Composable
fun PillChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.inverseSurface
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = when {
                active -> MaterialTheme.colorScheme.inverseOnSurface
                enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.outline
            },
        )
    }
}