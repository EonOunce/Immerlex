package com.immerlex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** 一条小鱼干的剪影：比图标库里任何一个符号都贴题，也不必引入 emoji。 */
@Composable
fun FishIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Canvas(modifier) { drawFish(tint) }
}

@Composable
fun FishPill(balance: Int, modifier: Modifier = Modifier) {
    val fishColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = fishColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FishIcon(Modifier.size(16.dp), fishColor)
            Text(balance.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun DrawScope.drawFish(color: Color) {
    val width = size.width
    val height = size.height
    val body = Path().apply {
        addOval(Rect(0f, height * 0.22f, width * 0.74f, height * 0.78f))
    }
    drawPath(body, color)
    val tail = Path().apply {
        moveTo(width * 0.68f, height * 0.5f)
        lineTo(width, height * 0.16f)
        lineTo(width, height * 0.84f)
        close()
    }
    drawPath(tail, color)
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = width * 0.05f,
        center = Offset(width * 0.21f, height * 0.42f),
    )
}
