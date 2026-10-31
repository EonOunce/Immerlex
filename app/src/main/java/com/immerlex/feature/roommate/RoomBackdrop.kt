package com.immerlex.feature.roommate

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.immerlex.app.ui.LocalAppearance

/** 房间的固定配色；暖色只在这里，不跟主题抢纸色。 */
@Immutable
data class RoomPalette(
    val dark: Boolean,
    val wall: Color,
    val wallLine: Color,
    val floor: Color,
    val floorLine: Color,
    val skyTop: Color,
    val skyBottom: Color,
    val hill: Color,
    val wood: Color,
    val curtain: Color,
    val rug: Color,
    val rugLine: Color,
    val leaf: Color,
    val pot: Color,
    val bubble: Color,
    val pet: Color,
    val petInk: Color,
    val blush: Color,
)

private val LightRoom = RoomPalette(
    dark = false,
    wall = Color(0xFFF7EFE1),
    wallLine = Color(0xFFEDE0CB),
    floor = Color(0xFFDDB68B),
    floorLine = Color(0xFFCBA076),
    skyTop = Color(0xFFDCEDF2),
    skyBottom = Color(0xFFF3F7ED),
    hill = Color(0xFFCBDCC4),
    wood = Color(0xFFC08A5C),
    curtain = Color(0xFFEFD9BC),
    rug = Color(0xFFEFE2CE),
    rugLine = Color(0xFFDECCAD),
    leaf = Color(0xFF6E9B70),
    pot = Color(0xFFB96A52),
    bubble = Color(0xFFFFFDF8),
    pet = Color(0xFFCBB9A3),
    petInk = Color(0xFF4A3F34),
    blush = Color(0xFFE8A6A0),
)

private val DarkRoom = RoomPalette(
    dark = true,
    wall = Color(0xFF2B2721),
    wallLine = Color(0xFF352F27),
    floor = Color(0xFF4B3A2A),
    floorLine = Color(0xFF5A4634),
    skyTop = Color(0xFF23343C),
    skyBottom = Color(0xFF2E3B33),
    hill = Color(0xFF35503E),
    wood = Color(0xFF6A4F33),
    curtain = Color(0xFF5B4630),
    rug = Color(0xFF3F3528),
    rugLine = Color(0xFF514431),
    leaf = Color(0xFF5F8A63),
    pot = Color(0xFF8E5140),
    bubble = Color(0xFF363029),
    pet = Color(0xFF6E5F4C),
    petInk = Color(0xFFF1E9DC),
    blush = Color(0xFFB97C74),
)

@Composable
fun roomPalette(): RoomPalette =
    if (LocalAppearance.current.darkMode) DarkRoom else LightRoom

/**
 * 一整间房：墙、窗、挂钟、木地板、地毯、边桌和盆栽。
 * 全部用色块画，跟应用其它页面的纸色/锈色是同一套脾气；宠物 GIF 叠在上面。
 */
@Composable
fun RoomBackdrop(modifier: Modifier = Modifier) {
    val p = roomPalette()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val px = 1.dp.toPx()
        val floorTop = h * 0.70f

        drawRect(p.wall, size = Size(w, floorTop))
        drawRect(p.wallLine, topLeft = Offset(0f, floorTop - 8 * px), size = Size(w, 8 * px))
        drawRect(p.floor, topLeft = Offset(0f, floorTop), size = Size(w, h - floorTop))

        // 地板：四条横缝，竖缝错开，别像瓷砖。
        val plank = (h - floorTop) / 4f
        for (i in 1..3) {
            val y = floorTop + plank * i
            drawLine(p.floorLine, Offset(0f, y), Offset(w, y), strokeWidth = px)
        }
        val seams = listOf(0.30f, 0.62f, 0.18f, 0.78f)
        seams.forEachIndexed { row, x ->
            val top = floorTop + plank * row
            drawLine(
                p.floorLine,
                Offset(w * x, top),
                Offset(w * x, top + plank),
                strokeWidth = px,
            )
        }

        // 窗：木框、天空、太阳、两层小山。
        val win = Offset(w * 0.055f, h * 0.075f)
        val winSize = Size(w * 0.40f, h * 0.30f)
        drawRoundRect(p.wood, win, winSize, CornerRadius(10 * px))
        val inset = 5 * px
        val glass = Offset(win.x + inset, win.y + inset)
        val glassSize = Size(winSize.width - inset * 2, winSize.height - inset * 2)
        clipRect(glass.x, glass.y, glass.x + glassSize.width, glass.y + glassSize.height) {
            drawRect(
                Brush.verticalGradient(
                    listOf(p.skyTop, p.skyBottom),
                    startY = glass.y,
                    endY = glass.y + glassSize.height,
                ),
                topLeft = glass,
                size = glassSize,
            )
            drawCircle(
                Color(0xFFF2D98B),
                radius = glassSize.width * 0.10f,
                center = Offset(glass.x + glassSize.width * 0.72f, glass.y + glassSize.height * 0.28f),
            )
            drawOval(
                p.hill.copy(alpha = 0.85f),
                topLeft = Offset(glass.x - glassSize.width * 0.12f, glass.y + glassSize.height * 0.58f),
                size = Size(glassSize.width * 0.85f, glassSize.height * 0.62f),
            )
            drawOval(
                p.hill,
                topLeft = Offset(glass.x + glassSize.width * 0.42f, glass.y + glassSize.height * 0.66f),
                size = Size(glassSize.width * 0.78f, glassSize.height * 0.55f),
            )
        }
        drawLine(
            p.wood,
            Offset(win.x + winSize.width / 2, win.y + inset),
            Offset(win.x + winSize.width / 2, win.y + winSize.height - inset),
            strokeWidth = 3 * px,
        )
        drawLine(
            p.wood,
            Offset(win.x + inset, win.y + winSize.height / 2),
            Offset(win.x + winSize.width - inset, win.y + winSize.height / 2),
            strokeWidth = 3 * px,
        )
        drawRoundRect(
            p.wood,
            Offset(win.x - 6 * px, win.y + winSize.height),
            Size(winSize.width + 12 * px, 7 * px),
            CornerRadius(3 * px),
        )
        // 窗帘挂在窗子右边，垂到地板线上方。
        drawRoundRect(
            p.curtain,
            Offset(win.x + winSize.width - 6 * px, win.y - 6 * px),
            Size(winSize.width * 0.16f, winSize.height + 26 * px),
            CornerRadius(8 * px),
        )
        drawLine(
            p.wallLine,
            Offset(win.x + winSize.width + 2 * px, win.y + 4 * px),
            Offset(win.x + winSize.width + 2 * px, win.y + winSize.height + 14 * px),
            strokeWidth = 2 * px,
        )

        // 窗光落在地上。
        val light = Path().apply {
            moveTo(win.x + winSize.width * 0.15f, floorTop)
            lineTo(win.x + winSize.width * 0.75f, floorTop)
            lineTo(win.x + winSize.width * 1.35f, h)
            lineTo(win.x + winSize.width * 0.35f, h)
            close()
        }
        drawPath(light, Color.White.copy(alpha = if (p.dark) 0.05f else 0.12f))

        // 挂钟。
        val clock = Offset(w * 0.865f, h * 0.145f)
        val clockR = (h * 0.052f).coerceAtLeast(16 * px)
        drawCircle(p.bubble, clockR, clock)
        drawCircle(p.wood, clockR, clock, style = Stroke(3 * px))
        drawLine(p.petInk, clock, Offset(clock.x, clock.y - clockR * 0.58f), strokeWidth = 2.5f * px)
        drawLine(p.petInk, clock, Offset(clock.x + clockR * 0.42f, clock.y + clockR * 0.18f), strokeWidth = 2.5f * px)
        drawCircle(p.petInk, 2 * px, clock)

        // 地毯和它的小影子。
        val rugCenter = Offset(w * 0.5f, h * 0.855f)
        val rugSize = Size(w * 0.66f, h * 0.13f)
        val rugTop = Offset(rugCenter.x - rugSize.width / 2, rugCenter.y - rugSize.height / 2)
        drawOval(p.rug, rugTop, rugSize)
        drawOval(p.rugLine, rugTop, rugSize, style = Stroke(2 * px))
        drawOval(
            p.rugLine.copy(alpha = 0.55f),
            Offset(rugTop.x + rugSize.width * 0.12f, rugTop.y + rugSize.height * 0.16f),
            Size(rugSize.width * 0.76f, rugSize.height * 0.68f),
            style = Stroke(px),
        )
        drawOval(
            Color.Black.copy(alpha = if (p.dark) 0.28f else 0.10f),
            Offset(w * 0.33f, h * 0.845f),
            Size(w * 0.34f, h * 0.045f),
        )

        drawTableAndPlant(p, w, h, px)
    }
}

private fun DrawScope.drawTableAndPlant(p: RoomPalette, w: Float, h: Float, px: Float) {
    val topY = h * 0.60f
    val tableX = w * 0.70f
    val tableW = w * 0.30f
    drawRoundRect(p.wood, Offset(tableX, topY), Size(tableW, 7 * px), CornerRadius(3 * px))
    drawRect(p.wood, Offset(tableX + 6 * px, topY), Size(6 * px, h * 0.13f))
    drawRect(p.wood, Offset(tableX + tableW - 14 * px, topY), Size(6 * px, h * 0.13f))

    // 桌角一摞小书。
    drawRoundRect(p.curtain, Offset(tableX + tableW * 0.10f, topY - 16 * px), Size(26 * px, 9 * px), CornerRadius(2 * px))
    drawRoundRect(p.leaf.copy(alpha = 0.65f), Offset(tableX + tableW * 0.14f, topY - 25 * px), Size(24 * px, 9 * px), CornerRadius(2 * px))

    // 盆栽：陶盆加三片叶子。
    val potCenter = Offset(tableX + tableW * 0.60f, topY - 20 * px)
    drawRoundRect(
        p.pot,
        Offset(potCenter.x - 13 * px, potCenter.y - 12 * px),
        Size(26 * px, 22 * px),
        CornerRadius(4 * px),
    )
    rotate(-32f, potCenter) {
        drawOval(p.leaf, Offset(potCenter.x - 6 * px, potCenter.y - 38 * px), Size(12 * px, 28 * px))
    }
    drawOval(p.leaf, Offset(potCenter.x - 6 * px, potCenter.y - 44 * px), Size(12 * px, 32 * px))
    rotate(32f, potCenter) {
        drawOval(p.leaf, Offset(potCenter.x - 6 * px, potCenter.y - 38 * px), Size(12 * px, 28 * px))
    }
}

/** 没有素材时的替身：一只圆乎乎的小东西，房间不至于是空的。 */
@Composable
fun PetSilhouette(modifier: Modifier = Modifier) {
    val p = roomPalette()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val px = 1.dp.toPx()
        drawCircle(p.pet, w * 0.13f, Offset(w * 0.26f, h * 0.22f))
        drawCircle(p.pet, w * 0.13f, Offset(w * 0.74f, h * 0.22f))
        drawOval(p.pet, Offset(w * 0.17f, h * 0.16f), Size(w * 0.66f, h * 0.76f))
        drawOval(p.bubble.copy(alpha = 0.45f), Offset(w * 0.30f, h * 0.44f), Size(w * 0.40f, h * 0.42f))
        drawCircle(p.blush.copy(alpha = 0.45f), w * 0.065f, Offset(w * 0.30f, h * 0.53f))
        drawCircle(p.blush.copy(alpha = 0.45f), w * 0.065f, Offset(w * 0.70f, h * 0.53f))
        drawCircle(p.petInk, w * 0.034f, Offset(w * 0.40f, h * 0.42f))
        drawCircle(p.petInk, w * 0.034f, Offset(w * 0.60f, h * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.012f, Offset(w * 0.41f, h * 0.405f))
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.012f, Offset(w * 0.61f, h * 0.405f))
        drawArc(
            p.petInk,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.46f, h * 0.46f),
            size = Size(w * 0.08f, h * 0.045f),
            style = Stroke(width = 2 * px),
        )
    }
}
