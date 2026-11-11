package com.immerlex.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.LocalAccent
import com.immerlex.core.schedule.HeatDay
import com.immerlex.core.schedule.TrendDay

/**
 * 今日页唯一的一张"近况"卡。上面 13 张竖纸条是热力图（一条一周，周一在上往下排），
 * 下面那条平滑线是近 30 天——纸条回答"哪几天动过手"，线只回答"这一段整体在往上还是在往下"。
 * 两件事说的是同一批日子，所以并成一张卡，不再上下各摆一张。
 */
@Composable
internal fun RecentCard(days: List<HeatDay>, trend: List<TrendDay>) {
    val accent = LocalAccent.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HeatStrips(days = days, accent = accent, modifier = Modifier.fillMaxWidth().height(72.dp))
            TrendLine(days = trend, accent = accent, modifier = Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

/** 一条纸条一周：7 格从上往下是周一到周日，纸条之间的缝比格子之间的缝大，好数出今天在第几张。 */
@Composable
private fun HeatStrips(days: List<HeatDay>, accent: Color, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(7).forEach { week ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { day ->
                    val color = if (day.level == 0) empty else accent.copy(alpha = 0.25f + 0.19f * day.level)
                    Box(Modifier.fillMaxWidth().weight(1f).background(color, RoundedCornerShape(2.dp)))
                }
                // 最后那张纸条本周还没过完：留空位，13 张纸条才一样长。
                repeat(7 - week.size) {
                    Spacer(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/**
 * 近 30 天连成一条平滑曲线，线下铺一层很淡的主色，最后那个点是今天。
 * 全月一个词都没碰时贴在中线：塌在底边会让人以为图坏了。
 */
@Composable
private fun TrendLine(days: List<TrendDay>, accent: Color, modifier: Modifier = Modifier) {
    val hole = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier) {
        if (days.size < 2) return@Canvas
        val max = days.maxOfOrNull { it.words } ?: 0
        val floor = size.height - 1.dp.toPx()
        val step = size.width / (days.size - 1)
        val points = days.mapIndexed { index, day ->
            val ratio = if (max == 0) 0.4f else day.words.toFloat() / max
            Offset(index * step, floor - (floor - 5.dp.toPx()) * ratio)
        }
        val curve = smoothPath(points)
        drawPath(path = fillUnder(curve, points, floor), brush = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.02f)),
            startY = 0f,
            endY = size.height,
        ))
        drawPath(curve, color = accent, style = Stroke(width = 1.6.dp.toPx()))
        val today = points.last()
        drawCircle(color = accent, radius = 3.6.dp.toPx(), center = today)
        drawCircle(color = hole, radius = 1.5.dp.toPx(), center = today)
    }
}

/** Catmull-Rom 折成三次贝塞尔：跟着点的走向弯，不过冲、不凭空造出一个新峰。 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points.first() }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { points.last() }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

private fun fillUnder(curve: Path, points: List<Offset>, bottom: Float): Path = Path().apply {
    addPath(curve)
    lineTo(points.last().x, bottom)
    lineTo(points.first().x, bottom)
    close()
}
